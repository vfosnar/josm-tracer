/**
 *  Tracer - plugin for JOSM
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openstreetmap.josm.plugins.tracer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * S-JTSK / Krovak East North (EPSG:5514) to and from ETRS89 by the ČÚZK transformation
 * (EPSG:5514 -> 5516 table, Modified Krovak, Helmert EPSG:5226), as PROJ's ETRS89_TO_S_JTSK_E_N.
 * Port of PROJ 9.7 krovak.cpp and gridshift.cpp (biquadratic); see doc/cuzk-transform.md.
 */
public final class SJtsk {

    private static final String GRID = "/data/cz_cuzk_table_-y-x_3_v1710.txt.gz";

    // Bessel 1841 and the Krovak parameters of EPSG:5514/5516
    private static final double A = 6377397.155;
    private static final double ES = 0.006674372230614;
    private static final double E = Math.sqrt(ES);
    private static final double PHI0 = Math.toRadians(49.5);
    private static final double LAM0 = Math.toRadians(24.8333333333333);
    private static final double K0 = 0.9999;
    private static final double S0 = 1.37008346281555;
    private static final double UQ = 1.04216856380474;
    private static final double ALPHA = Math.sqrt(1 + ES * Math.pow(Math.cos(PHI0), 4) / (1 - ES));
    private static final double KK;
    private static final double N = Math.sin(S0);
    private static final double RHO0;
    private static final double AD = Math.PI / 2 - UQ;

    static {
        double u0 = Math.asin(Math.sin(PHI0) / ALPHA);
        double g = Math.pow((1 + E * Math.sin(PHI0)) / (1 - E * Math.sin(PHI0)), ALPHA * E / 2);
        KK = Math.tan(u0 / 2 + Math.PI / 4) / Math.pow(Math.tan(PHI0 / 2 + Math.PI / 4), ALPHA) * g;
        double n0 = Math.sqrt(1 - ES) / (1 - ES * Math.pow(Math.sin(PHI0), 2));
        RHO0 = K0 * n0 / Math.tan(S0);
    }

    // Modified Krovak (EPSG method 1042) polynomial
    private static final double MX0 = 1089000.0;
    private static final double MY0 = 654000.0;
    private static final double C1 = 2.946529277E-02;
    private static final double C2 = 2.515965696E-02;
    private static final double C3 = 1.193845912E-07;
    private static final double C4 = -4.668270147E-07;
    private static final double C5 = 9.233980362E-12;
    private static final double C6 = 1.523735715E-12;
    private static final double C7 = 1.696780024E-18;
    private static final double C8 = 4.408314235E-18;
    private static final double C9 = -8.331083518E-24;
    private static final double C10 = -3.689471323E-24;

    // S-JTSK/05 -> ETRS89, coordinate frame rotation (EPSG:5226)
    private static final double SEC = Math.PI / 180 / 3600;
    private static final double TX = 572.213;
    private static final double TY = 85.334;
    private static final double TZ = 461.94;
    private static final double RX = -4.9732 * SEC;
    private static final double RY = -1.529 * SEC;
    private static final double RZ = -5.2484 * SEC;
    private static final double SCALE = 1 + 3.5378e-6;

    private static final double GRS80_A = 6378137.0;
    private static final double GRS80_ES = 0.00669438002290;

    private static Grid grid;

    private SJtsk() {
    }

    /**
     * @param en S-JTSK / Krovak East North coordinates (EPSG:5514)
     * @return ETRS89 position
     * @throws IOException Position outside the ČÚZK table, or the table is unreadable
     */
    public static LatLon toEtrs89(EastNorth en) throws IOException {
        double[] shift = grid().shift(en.east(), en.north());
        double[] bessel = inverseModKrovak(-(en.north() + shift[1]), -(en.east() + shift[0]));
        double[] xyz = helmert(cartesian(bessel[0], bessel[1], A, ES));
        double[] etrs = geodetic(xyz, GRS80_A, GRS80_ES);
        return new LatLon(Math.toDegrees(etrs[0]), Math.toDegrees(etrs[1]));
    }

    /**
     * @param ll ETRS89 position
     * @return S-JTSK / Krovak East North coordinates (EPSG:5514)
     * @throws IOException Position outside the ČÚZK table, or the table is unreadable
     */
    public static EastNorth fromEtrs89(LatLon ll) throws IOException {
        double[] xyz = inverseHelmert(cartesian(Math.toRadians(ll.lat()), Math.toRadians(ll.lon()), GRS80_A, GRS80_ES));
        double[] bessel = geodetic(xyz, A, ES);
        double[] sw = modKrovak(bessel[0], bessel[1]);
        double e = -sw[1];
        double n = -sw[0];
        // PROJ inverts a biquadratic grid shift without iterating
        double[] shift = grid().shift(e, n);
        return new EastNorth(e - shift[0], n - shift[1]);
    }

    /** @return (southing, westing) in metres */
    private static double[] modKrovak(double phi, double lam) {
        double gfi = Math.pow((1 + E * Math.sin(phi)) / (1 - E * Math.sin(phi)), ALPHA * E / 2);
        double u = 2 * (Math.atan(KK * Math.pow(Math.tan(phi / 2 + Math.PI / 4), ALPHA) / gfi) - Math.PI / 4);
        double deltav = -(lam - LAM0) * ALPHA;
        double s = Math.asin(Math.cos(AD) * Math.sin(u) + Math.sin(AD) * Math.cos(u) * Math.cos(deltav));
        double d = Math.asin(Math.cos(u) * Math.sin(deltav) / Math.cos(s));
        double eps = N * d;
        double rho = RHO0 * Math.pow(Math.tan(S0 / 2 + Math.PI / 4), N) / Math.pow(Math.tan(s / 2 + Math.PI / 4), N);
        double x = rho * Math.cos(eps) * A;
        double y = rho * Math.sin(eps) * A;
        double[] dxy = modKrovakCorrection(x - MX0, y - MY0);
        return new double[] {x - dxy[0], y - dxy[1]};
    }

    /** @return Bessel (phi, lambda) in radians */
    private static double[] inverseModKrovak(double south, double west) throws IOException {
        double[] dxy = modKrovakCorrection(south - MX0, west - MY0);
        double x = (south + dxy[0]) / A;
        double y = (west + dxy[1]) / A;
        double rho = Math.hypot(x, y);
        double d = Math.atan2(y, x) / Math.sin(S0);
        double s = 2 * (Math.atan(Math.pow(RHO0 / rho, 1 / N) * Math.tan(S0 / 2 + Math.PI / 4)) - Math.PI / 4);
        double u = Math.asin(Math.cos(AD) * Math.sin(s) - Math.sin(AD) * Math.cos(s) * Math.cos(d));
        double deltav = Math.asin(Math.cos(s) * Math.sin(d) / Math.cos(u));
        double lam = LAM0 - deltav / ALPHA;
        double fi1 = u;
        for (int i = 0; i < 100; i++) {
            double phi = 2 * (Math.atan(Math.pow(KK, -1 / ALPHA) * Math.pow(Math.tan(u / 2 + Math.PI / 4), 1 / ALPHA)
                * Math.pow((1 + E * Math.sin(fi1)) / (1 - E * Math.sin(fi1)), E / 2)) - Math.PI / 4);
            if (Math.abs(fi1 - phi) < 1e-15)
                return new double[] {phi, lam};
            fi1 = phi;
        }
        throw new IOException("S-JTSK position " + west + ", " + south + " did not converge in inverse Krovak");
    }

    private static double[] modKrovakCorrection(double xr, double yr) {
        double x2 = xr * xr, y2 = yr * yr, x4 = x2 * x2, y4 = y2 * y2;
        double dx = C1 + C3 * xr - C4 * yr - 2 * C6 * xr * yr + C5 * (x2 - y2) + C7 * xr * (x2 - 3 * y2)
            - C8 * yr * (3 * x2 - y2) + 4 * C9 * xr * yr * (x2 - y2) + C10 * (x4 + y4 - 6 * x2 * y2);
        double dy = C2 + C3 * yr + C4 * xr + 2 * C5 * xr * yr + C6 * (x2 - y2) + C8 * xr * (x2 - 3 * y2)
            + C7 * yr * (3 * x2 - y2) - 4 * C10 * xr * yr * (x2 - y2) + C9 * (x4 + y4 - 6 * x2 * y2);
        return new double[] {dx, dy};
    }

    private static double[] cartesian(double phi, double lam, double a, double es) {
        double n = a / Math.sqrt(1 - es * Math.sin(phi) * Math.sin(phi));
        return new double[] {
            n * Math.cos(phi) * Math.cos(lam),
            n * Math.cos(phi) * Math.sin(lam),
            n * (1 - es) * Math.sin(phi)};
    }

    private static double[] geodetic(double[] xyz, double a, double es) {
        double p = Math.hypot(xyz[0], xyz[1]);
        double phi = Math.atan2(xyz[2], p * (1 - es));
        for (int i = 0; i < 10; i++) {
            double n = a / Math.sqrt(1 - es * Math.sin(phi) * Math.sin(phi));
            double h = p / Math.cos(phi) - n;
            phi = Math.atan2(xyz[2], p * (1 - es * n / (n + h)));
        }
        return new double[] {phi, Math.atan2(xyz[1], xyz[0])};
    }

    private static double[] helmert(double[] v) {
        return new double[] {
            TX + SCALE * (v[0] + RZ * v[1] - RY * v[2]),
            TY + SCALE * (-RZ * v[0] + v[1] + RX * v[2]),
            TZ + SCALE * (RY * v[0] - RX * v[1] + v[2])};
    }

    private static double[] inverseHelmert(double[] v) {
        double x = (v[0] - TX) / SCALE, y = (v[1] - TY) / SCALE, z = (v[2] - TZ) / SCALE;
        return new double[] {
            x - RZ * y + RY * z,
            RZ * x + y - RX * z,
            -RY * x + RX * y + z};
    }

    private static synchronized Grid grid() throws IOException {
        if (grid == null)
            grid = Grid.load();
        return grid;
    }

    /** Easting/northing offsets on a regular S-JTSK grid, rows from the south. */
    private static final class Grid {
        final int cols, rows;
        final double west, south, step;
        final float[] de, dn;

        private Grid(int cols, int rows, double west, double south, double step) {
            this.cols = cols;
            this.rows = rows;
            this.west = west;
            this.south = south;
            this.step = step;
            de = new float[cols * rows];
            dn = new float[cols * rows];
        }

        static Grid load() throws IOException {
            InputStream res = SJtsk.class.getResourceAsStream(GRID);
            if (res == null)
                throw new IOException("ČÚZK transformation table " + GRID + " is missing from the plugin");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(res), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                while (line != null && line.startsWith("#"))
                    line = r.readLine();
                if (line == null)
                    throw new IOException(GRID + " has no header");
                String[] h = line.trim().split(" ");
                int cols = Integer.parseInt(h[0]), rows = Integer.parseInt(h[1]);
                double step = Double.parseDouble(h[4]);
                Grid g = new Grid(cols, rows, Double.parseDouble(h[2]), Double.parseDouble(h[3]) - (rows - 1) * step, step);
                for (int fromNorth = 0; fromNorth < rows; fromNorth++) {
                    for (int c = 0; c < cols; c++) {
                        line = r.readLine();
                        if (line == null)
                            throw new IOException(GRID + " ends early at row " + fromNorth + ", column " + c);
                        int i = (rows - 1 - fromNorth) * cols + c;
                        if (line.equals("-")) {
                            g.de[i] = g.dn[i] = Float.NaN;
                        } else {
                            String[] v = line.split(" ");
                            g.de[i] = Float.parseFloat(v[0]);
                            g.dn[i] = Float.parseFloat(v[1]);
                        }
                    }
                }
                if (r.readLine() != null)
                    throw new IOException(GRID + " has more nodes than its header says");
                return g;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                throw new IOException(GRID + " is malformed: " + e.getMessage(), e);
            }
        }

        /** Biquadratic interpolation as PROJ gridshift.cpp; @return (dE, dN) */
        double[] shift(double e, double n) throws IOException {
            double x = (e - west) / step, y = (n - south) / step;
            if (!(x >= 0 && x <= cols - 1 && y >= 0 && y <= rows - 1))
                throw outside(e, n);
            int ix = Math.min((int) Math.floor(x), cols - 2), iy = Math.min((int) Math.floor(y), rows - 2);
            double fx = x - ix, fy = y - iy;
            if ((fx <= 0.5 && ix > 0) || ix + 2 == cols) {
                ix--;
                fx++;
            }
            if ((fy <= 0.5 && iy > 0) || iy + 2 == rows) {
                iy--;
                fy++;
            }
            double[] out = new double[2];
            float[][] bands = {de, dn};
            for (int b = 0; b < 2; b++) {
                double[] row = new double[3];
                for (int j = 0; j < 3; j++) {
                    int i = (iy + j) * cols + ix;
                    row[j] = quadratic(fx, bands[b][i], bands[b][i + 1], bands[b][i + 2]);
                }
                out[b] = quadratic(fy, row[0], row[1], row[2]);
                if (Double.isNaN(out[b]))
                    throw outside(e, n);
            }
            return out;
        }

        private static double quadratic(double x, double f0, double f1, double f2) {
            double d0 = f1 - f0;
            return f0 + x * d0 + 0.5 * x * (x - 1) * (f2 - f1 - d0);
        }

        private static IOException outside(double e, double n) {
            return new IOException(String.format("S-JTSK position %.0f, %.0f is outside the ČÚZK transformation table (Czechia only)", e, n));
        }
    }
}
