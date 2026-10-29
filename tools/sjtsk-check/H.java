import java.io.*; import org.openstreetmap.josm.data.coor.*; import org.openstreetmap.josm.plugins.tracer.SJtsk;
public class H { public static void main(String[] a) throws Exception {
  BufferedReader r=new BufferedReader(new InputStreamReader(System.in)); String l;
  while((l=r.readLine())!=null){ String[] f=l.trim().split("\\s+"); double x=Double.parseDouble(f[0]),y=Double.parseDouble(f[1]);
    try { if(a[0].equals("inv")){ LatLon ll=SJtsk.toEtrs89(new EastNorth(x,y)); System.out.printf("%.12f %.12f%n",ll.lat(),ll.lon()); }
          else { EastNorth en=SJtsk.fromEtrs89(new LatLon(x,y)); System.out.printf("%.6f %.6f%n",en.east(),en.north()); } }
    catch(IOException e){ System.out.println("ERR "+e.getMessage()); } } } }
