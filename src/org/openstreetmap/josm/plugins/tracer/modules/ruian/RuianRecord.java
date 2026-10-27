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

package org.openstreetmap.josm.plugins.tracer.modules.ruian;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.tracer.CuzkRuian;
import org.openstreetmap.josm.plugins.tracer.TracerRecord;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;


/**
 * The Tracer RUIAN record class
 *
 */

public final class RuianRecord extends TracerRecord {

    private String   m_source;
    private long     m_ruian_id;
    private int      m_levels;
    private int      m_flats;
    private String   m_usage_code;
    private String   m_usage_key;
    private String   m_usage_val;
    private String   m_finished;
    private String   m_valid_from;

    private ArrayList <Address> m_address_places;

    /** building=* per SC_ZP_VYUZITI_BUD code as poloha.net tagged it: wiki Cs:RÚIAN, column "Současný stav dle Petra" */
    private static final Map<Integer, String> BUILDING_BY_USAGE = Map.ofEntries(
        Map.entry(1, "industrial"), Map.entry(2, "farm_auxiliary"), Map.entry(3, "residential"),
        Map.entry(4, "yes"), Map.entry(5, "civic"), Map.entry(6, "residential"),
        Map.entry(7, "house"), Map.entry(8, "yes"), Map.entry(9, "civic"),
        Map.entry(10, "commercial"), Map.entry(11, "hotel"), Map.entry(12, "warehouse"),
        Map.entry(13, "barn"), Map.entry(14, "commercial"), Map.entry(15, "civic"),
        Map.entry(16, "industrial"), Map.entry(17, "transportation"), Map.entry(18, "garage"),
        Map.entry(19, "yes"), Map.entry(20, "yes"), Map.entry(21, "greenhouse"),
        Map.entry(27, "transportation"), Map.entry(28, "industrial"), Map.entry(29, "yes"));

    public RuianRecord (double adjlat, double adjlon) {
        super (adjlat, adjlon);
        this.init();
    }

    @Override
    protected void init() {
        super.init();
        m_source = "";
        m_ruian_id = -1;
        m_levels = 0;
        m_flats = 0;
        m_usage_code = "";
        m_usage_key = "";
        m_usage_val = "";
        m_finished = "";
        m_valid_from = "";
        m_address_places = new ArrayList<>();
    }

    /**
     * Parse given JSON string and fill record with RUIAN data
     *
     * @param jsonStr JSON string with RUIAN data
     */
    public void parseJSON(String jsonStr) {

        init();

        long adr_id = 0;
        String house_number = "";
        String house_number_typ = "";
        String street_number = "";
        String street = "";
        String place = "";
        String suburb = "";
        String city = "";
        String district = "";
        String region = "";
        String postcode = "";

        JsonObject obj;
        try (JsonReader jsonReader = Json.createReader(new ByteArrayInputStream(jsonStr.getBytes()))) {
            obj = jsonReader.readObject();
        }

        // get source
        m_source = parseJsonString(obj, "source", m_source);

        // no geometry? leave record without data
        JsonObject geomObj = retrieveJsonObject(obj, "geometry");
        if (geomObj == null)
            return;

        // outer geometry
        JsonArray outerArr = geomObj.getJsonArray("outer");
        List<LatLon> way = new ArrayList<>(outerArr.size());
        for (int i = 0; i < outerArr.size(); i++) {
            JsonArray node = outerArr.getJsonArray(i);
            LatLon coor = new LatLon(
                    LatLon.roundToOsmPrecision(node.getJsonNumber(1).doubleValue()),
                    LatLon.roundToOsmPrecision(node.getJsonNumber(0).doubleValue())
            );
            System.out.println("outer[" + i + "]:coor: " + coor.toString());
            way.add(coor);
        }
        super.setOuter(way);

        // inner geometries
        JsonArray innersArr = retrieveJsonArray(geomObj, "inners");
        if (innersArr != null) {
            for (int i = 0; i < innersArr.size(); i++) {
                JsonArray innerArr = innersArr.getJsonArray(i);
                ArrayList<LatLon> inner = new ArrayList<>();

                System.out.println("");
                for (int j = 0; j < innerArr.size(); j++) {
                    JsonArray node = innerArr.getJsonArray(j);
                    LatLon coor = new LatLon(
                            LatLon.roundToOsmPrecision(node.getJsonNumber(1).doubleValue()),
                            LatLon.roundToOsmPrecision(node.getJsonNumber(0).doubleValue())
                    );
                    System.out.println("inner[" + i + "][" + j + "]:coor: " + coor.toString());
                    inner.add(coor);
                }
                super.addInner(inner);
            }
        }

        // SO data
        JsonObject building = retrieveJsonObject(obj, "stavebni_objekt");
        if (building != null) {
            m_ruian_id = parseJsonLong(building, "ruian_id", m_ruian_id);
            house_number = parseJsonString(building, "cislo_domovni", house_number);
            house_number_typ = parseJsonString(building, "cislo_domovni_typ", house_number_typ);
            street_number = parseJsonString(building, "cislo_orientacni", street_number);
            adr_id = parseJsonLong(building, "adresni_misto_kod", adr_id);
            street = parseJsonString(building, "ulice", street);
            place = parseJsonString(building, "cast_obce", place);
            suburb = parseJsonString(building, "mestska_cast", suburb);
            city = parseJsonString(building, "obec", city);
            district = parseJsonString(building, "okres", district);
            region = parseJsonString(building, "kraj", region);
            postcode = parseJsonString(building, "psc", postcode);
            m_levels = parseJsonInt(building, "pocet_podlazi", m_levels);
            m_flats = parseJsonInt(building, "pocet_bytu", m_flats);
            m_usage_code = parseJsonString(building, "zpusob_vyuziti_kod", m_usage_code);
            m_usage_key = parseJsonString(building, "zpusob_vyuziti_key", m_usage_key);
            m_usage_val = parseJsonString(building, "zpusob_vyuziti_val", m_usage_val);
            m_valid_from = parseJsonString(building, "plati_od", m_valid_from);
            m_finished = TracerUtils.convertDate(parseJsonString(building, "dokonceni", m_finished));
        }

        // address places
        JsonArray addrArr = retrieveJsonArray(obj, "adresni_mista");
        if (addrArr != null) {
            for (int i = 0; i < addrArr.size(); i++) {
                JsonObject addrPlace = addrArr.getJsonObject(i);
                Address addr = new Address();

                adr_id = parseJsonLong(addrPlace, "ruian_id", adr_id);
                house_number = parseJsonString(addrPlace, "cislo_domovni", house_number);
                street_number = parseJsonString(addrPlace, "cislo_orientacni", street_number);
                street = parseJsonString(addrPlace, "ulice", street);

                addr.setRuianID(adr_id);

                switch (house_number_typ) {
                    case "číslo popisné":
                        addr.setConscriptionNumber(house_number);
                        break;
                    case "číslo evidenční":
                        addr.setProvisionalNumber(house_number);
                        break;
                }

                addr.setStreetNumber(street_number);
                addr.setStreet(street);
                addr.setPlace(place);
                addr.setSuburb(suburb);
                addr.setCity(city);
                addr.setDistrict(district);
                addr.setRegion(region);
                addr.setCountryCode("CZ");
                addr.setPostCode(postcode);

                m_address_places.add(addr);
            }
        }

        // create address from SO data?
        if (m_address_places.isEmpty() && house_number.length() > 0) {
            Address addr = new Address();
            addr.setRuianID(adr_id);

            switch (house_number_typ) {
                case "číslo popisné":
                    addr.setConscriptionNumber(house_number);
                    break;
                case "číslo evidenční":
                    addr.setProvisionalNumber(house_number);
                    break;
            }

            addr.setStreetNumber(street_number);
            addr.setStreet(street);
            addr.setPlace(place);
            addr.setSuburb(suburb);
            addr.setCity(city);
            addr.setDistrict(district);
            addr.setRegion(region);
            addr.setCountryCode("CZ");
            addr.setPostCode(postcode);

            m_address_places.add(addr);
        }
    }

    /**
     * Fill record from a StavebniObjekt feature of the ČÚZK RÚIAN service
     *
     * @param feature Feature with attributes and geometry
     * @param pos Traced position, selects the part of a multi-part building
     * @throws IOException Geometry without any outer ring
     */
    public void parseCuzk(JsonObject feature, LatLon pos) throws IOException {

        init();
        m_source = "cuzk:ruian";

        CuzkRuian.Polygon part = CuzkRuian.partAt(feature.getJsonObject("geometry"), pos);
        super.setOuter(part.outer);
        for (List<LatLon> inner : part.inners)
            super.addInner(inner);

        JsonObject attrs = feature.getJsonObject("attributes");
        m_ruian_id = CuzkRuian.number(attrs, "kod");
        m_levels = (int) Math.max(0, CuzkRuian.number(attrs, "pocetpodlazi"));
        m_flats = (int) Math.max(0, CuzkRuian.number(attrs, "pocetbytu"));
        long usage = CuzkRuian.number(attrs, "zpusobvyuzitikod");
        if (usage > 0) {
            m_usage_code = Long.toString(usage);
            m_usage_key = "building";
            m_usage_val = BUILDING_BY_USAGE.getOrDefault((int) usage, "");
        }
        m_finished = CuzkRuian.date(attrs, "dokonceni");
    }

    /**
     * Returns the number of levels in the building
     *
     * @return Number of levels
     */
    public String getBuildingLevels() {
        if (m_levels > 0) {
            return Integer.toString(m_levels);
        } else {
            return "";
        }
    }

    /**
     * Returns the number of flats in the building
     *
     * @return Number of flats
     */
    public String getBuildingFlats() {
        if (m_flats > 0) {
            return Integer.toString(m_flats);
        } else {
            return "";
        }
    }

    /**
     * Returns building's date of finish
     *
     * @return Date of finish
     */
    public String getBuildingFinished() {
        return m_finished;
    }

    /**
     * Returns RUIAN building usage code
     *
     * @return RUIAN building usage code
     */
    public String getBuildingUsageCode() {
        return m_usage_code;
    }

    /**
     * Returns key of building type
     *
     * @return Key of building type
     */
    public String getBuildingTagKey() {
        return m_usage_key;
    }

    /**
     * Returns type of building
     *
     * @return Type of building
     */
    public String getBuildingTagValue() {
        return m_usage_val;
    }

    /**
     * Returns Ruian ID of the building
     *
     * @return Building Ruian ID
     */
    public long getBuildingID() {
        return m_ruian_id;
    }

    /**
     * Returns data source
     *
     * @return Data source
     */
    public String getSource() {
        return m_source;
    }

    @Override
    public Map<String, String> getKeys(boolean m_alt) {

        Map<String, String> tags = new HashMap<>();

        if (!m_alt) {
            if (getBuildingTagKey().equals("building")
                    && getBuildingTagValue().length() > 0) {
                tags.put("building", getBuildingTagValue());
            } else {
                tags.put("building", "yes");
            }
        }

        if (getBuildingID() > 0) {
            tags.put("ref:ruian:building", Long.toString(getBuildingID()));
        }

        if (getBuildingUsageCode().length() > 0) {
            tags.put("building:ruian:type", getBuildingUsageCode());
        }

        if (getBuildingLevels().length() > 0) {
            tags.put("building:levels", getBuildingLevels());
        }

        if (getBuildingFlats().length() > 0) {
            tags.put("building:flats", getBuildingFlats());
        }

        if (getBuildingFinished().length() > 0) {
            tags.put("start_date", getBuildingFinished());
        }

        if (getSource().length() > 0) {
            tags.put("source", getSource());
        } else {
            tags.put("source", "cuzk:ruian");
        }

        return tags;
    }

    @Override
    public boolean hasData() {
        return this.getBuildingID() > 0 && super.hasOuter();
    }
}
