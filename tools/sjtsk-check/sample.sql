SELECT c.name, p.osm_id, p.tags->>'ref:ruian:building' AS kod, ST_AsGeoJSON(p.geom) AS g
FROM (VALUES ('Cheb',12.30,50.03,12.45,50.12),('KarlovyVary',12.80,50.20,12.95,50.26),('Plzen',13.30,49.70,13.45,49.78),('Decin',14.15,50.74,14.28,50.80),('Liberec',15.00,50.73,15.12,50.80),('Praha',14.35,50.03,14.55,50.12),('CeskeBudejovice',14.42,48.94,14.52,49.01),('Hradec',15.78,50.18,15.88,50.24),('Jihlava',15.55,49.37,15.63,49.42),('Brno',16.55,49.15,16.68,49.24),('Olomouc',17.22,49.56,17.30,49.62),('Ostrava',18.18,49.78,18.32,49.86),('Zlin',17.62,49.20,17.70,49.25),('Znojmo',16.01,48.84,16.09,48.88),('Jesenik',17.17,50.21,17.23,50.25)) AS c(name,x1,y1,x2,y2)
CROSS JOIN LATERAL (
  SELECT osm_id, tags, geom FROM postpass_polygon
  WHERE tags @> '{"source":"cuzk:ruian"}' AND tags ? 'ref:ruian:building'
    AND geom && ST_MakeEnvelope(c.x1,c.y1,c.x2,c.y2,4326)
  ORDER BY osm_id DESC LIMIT 25) p
