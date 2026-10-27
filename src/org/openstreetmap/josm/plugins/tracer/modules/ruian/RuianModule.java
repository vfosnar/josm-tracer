/**
 *  Tracer - plugin for JOSM
 *  Jan Bilak, Marian Kyral, Martin Svec
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
package org.openstreetmap.josm.plugins.tracer.modules.ruian;

import java.awt.Cursor;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.tracer.modules.building.BuildingTracerModule;
import org.openstreetmap.josm.plugins.tracer.CuzkRuian;
import org.openstreetmap.josm.plugins.tracer.RuianSource;
import org.openstreetmap.josm.plugins.tracer.TracerPreferences;
import org.openstreetmap.josm.plugins.tracer.TracerRecord;

import static org.openstreetmap.josm.tools.I18n.*;
import org.openstreetmap.josm.tools.ImageProvider;

public final class RuianModule extends BuildingTracerModule {

    protected boolean cancel;
    private boolean moduleEnabled;

    private final String ruianUrl = "http://josm.poloha.net";
    private final RuianSource m_source;

    public RuianModule(boolean enabled) {
        this(enabled, RuianSource.POLOHA_NET);
    }

    public RuianModule(boolean enabled, RuianSource source) {
        moduleEnabled = enabled;
        m_source = source;
    }

    @Override
    public void init() {
    }

    @Override
    public Cursor getCursor() {
        return ImageProvider.getCursor("crosshair", "tracer-ruian-sml");
    }

    @Override
    public Cursor getCursor(boolean ctrl, boolean alt, boolean shift) {
        if (ctrl){
            return ImageProvider.getCursor("crosshair", "tracer-ruian-new-sml");
        }
        if (shift){
            return ImageProvider.getCursor("crosshair", "tracer-ruian-tags-sml");
        }
        return ImageProvider.getCursor("crosshair", "tracer-ruian-sml");
    }

    @Override
    public String getName() {
        return m_source == RuianSource.CUZK ? tr("RUIAN-ČÚZK") : tr("RUIAN");
    }

    @Override
    public boolean moduleIsEnabled() {
        return moduleEnabled;
    };

    @Override
    public void setModuleIsEnabled(boolean enabled) {
        moduleEnabled = enabled;
    };

    @Override
    public AbstractTracerTask trace(final LatLon pos, final boolean ctrl, final boolean alt, final boolean shift) {
        return new RuianTracerTask (pos, ctrl, alt, shift);
    }

    class RuianTracerTask extends BuildingTracerTask {

        RuianTracerTask (LatLon pos, boolean ctrl, boolean alt, boolean shift) {
            super (pos, ctrl, alt ,shift);
        }

        @Override
        protected TracerRecord downloadRecord(LatLon pos) throws Exception {
            TracerPreferences pref = TracerPreferences.getInstance();

            // Get coordinate corrections
            double adjlat = 0, adjlon = 0;
            if (pref.isRuianAdjustPositionEnabled()) {
              adjlat = pref.getRuianAdjustPositionLat();
              adjlon = pref.getRuianAdjustPositionLon();
            }

            RuianServer server = new RuianServer();
            if (m_source == RuianSource.CUZK)
              return server.traceCuzk(m_pos, CuzkRuian.DEFAULT_URL, adjlat, adjlon);

            String sUrl = ruianUrl;
            if (pref.isCustomRuainUrlEnabled())
              sUrl = pref.getCustomRuainUrl();
            return server.trace(m_pos, sUrl, adjlat, adjlon);
        }
    }
}

