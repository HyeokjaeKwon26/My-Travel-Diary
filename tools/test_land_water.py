def point_in_poly(x, y, ring):
    n = len(ring)
    inside = False
    p1x, p1y = ring[0]
    for i in range(n + 1):
        p2x, p2y = ring[i % n]
        if y > min(p1y, p2y):
            if y <= max(p1y, p2y):
                if x <= max(p1x, p2x):
                    if p1y != p2y:
                        xinters = (y - p1y) * (p2x - p1x) / (p2y - p1y) + p1x
                    if p1x == p2x or x <= xinters:
                        inside = not inside
        p1x, p1y = p2x, p2y
    return inside

# Hudson River channel polygon (strictly between NJ shoreline and Manhattan west bank)
hudson_channel = [
    [-74.025, 40.700], [-74.011, 40.730], [-74.009, 40.744], [-73.999, 40.760],
    [-73.975, 40.790], [-73.938, 40.850], [-73.910, 40.900], [-73.925, 40.900],
    [-73.955, 40.850], [-73.995, 40.790], [-74.020, 40.760], [-74.025, 40.744],
    [-74.025, 40.730], [-74.035, 40.700], [-74.025, 40.700]
]

# East River channel polygon (strictly between Manhattan east bank and Brooklyn/Queens)
east_river_channel = [
    [-74.012, 40.700], [-73.985, 40.708], [-73.973, 40.718], [-73.963, 40.735],
    [-73.955, 40.755], [-73.940, 40.780], [-73.910, 40.800], [-73.925, 40.805],
    [-73.950, 40.780], [-73.968, 40.755], [-73.980, 40.735], [-73.988, 40.718],
    [-74.000, 40.708], [-74.012, 40.700]
]

# Upper NY Bay channel (east of Liberty Island, between Bayonne/Staten Island and Brooklyn)
upper_bay_channel = [
    [-74.050, 40.640], [-74.015, 40.640], [-74.015, 40.680], [-74.030, 40.700],
    [-74.038, 40.700], [-74.038, 40.675], [-74.050, 40.640]
]

points = {
    "Times Square": (-73.9855, 40.7580),
    "Wall Street": (-74.0090, 40.7074),
    "Lower Manhattan": (-74.0060, 40.7128),
    "Jersey City": (-74.0431, 40.7178),
    "Hoboken": (-74.0323, 40.7439),
    "Liberty Island": (-74.0445, 40.6892),
    "Boston Common": (-71.0656, 42.3550),
    "Mid-Hudson Channel": (-74.0180, 40.7300),
    "Upper NY Bay Channel": (-74.0300, 40.6800),
    "East River Channel": (-73.9850, 40.7150)
}

print("=== LAND / WATER CLASSIFICATION TEST ===")
all_pass = True
for name, (lng, lat) in points.items():
    in_hudson = point_in_poly(lng, lat, hudson_channel)
    in_east = point_in_poly(lng, lat, east_river_channel)
    in_bay = point_in_poly(lng, lat, upper_bay_channel)
    is_water = in_hudson or in_east or in_bay
    classification = "WATER" if is_water else "LAND"
    expected = "WATER" if "Channel" in name else "LAND"
    status = "OK" if classification == expected else "FAIL"
    if status == "FAIL":
        all_pass = False
    print(f"{name}: {classification} (expected: {expected}) -> {status}")

print(f"\nAll tests passed: {all_pass}")
