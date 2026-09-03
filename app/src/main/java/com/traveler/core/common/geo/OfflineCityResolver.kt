package com.traveler.core.common.geo

enum class LandmarkType {
    NATIONAL_PARK,
    NATURAL_WONDER,
    SCENIC_LANDMARK,
    CITY
}

data class LandmarkPoint(
    val name: String,
    val icon: String,
    val latitude: Double,
    val longitude: Double,
    val type: LandmarkType = LandmarkType.SCENIC_LANDMARK,
    val rank: Int = 1
)

data class CityPoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val rank: Int = 1
)

object OfflineCityResolver {

    // Comprehensive offline regional and global landmarks (National Parks, Natural Wonders)
    val MAJOR_LANDMARKS: List<LandmarkPoint> = listOf(
        // Iconic US National Parks & Natural Wonders
        LandmarkPoint("Grand Canyon", "🏞️", 36.1069, -112.1129, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Zion Nat'l Park", "🏜️", 37.2982, -113.0263, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Bryce Canyon", "🏜️", 37.5930, -112.1871, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Monument Valley", "🏜️", 36.9980, -110.0985, LandmarkType.NATURAL_WONDER, 1),
        LandmarkPoint("Arches Nat'l Park", "🏜️", 38.7331, -109.5925, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Valley of Fire", "🔥", 36.4889, -114.5325, LandmarkType.SCENIC_LANDMARK, 2),
        LandmarkPoint("Death Valley", "🏜️", 36.5323, -116.9325, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Yellowstone", "🌋", 44.4280, -110.5885, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Grand Teton", "🏔️", 43.7904, -110.6818, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Rocky Mountains", "🏔️", 40.3428, -105.6836, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Yosemite", "🌲", 37.8651, -119.5383, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Lake Tahoe", "🏖️", 39.0968, -120.0324, LandmarkType.NATURAL_WONDER, 1),
        LandmarkPoint("Hoover Dam", "⚡", 36.0161, -114.7377, LandmarkType.SCENIC_LANDMARK, 2),
        LandmarkPoint("Sedona", "🏜️", 34.8697, -111.7610, LandmarkType.NATURAL_WONDER, 2),
        LandmarkPoint("Antelope Canyon", "🏜️", 36.8619, -111.3743, LandmarkType.NATURAL_WONDER, 2),
        LandmarkPoint("Horseshoe Bend", "🏞️", 36.8791, -111.5105, LandmarkType.NATURAL_WONDER, 2),
        LandmarkPoint("Canyonlands", "🏞️", 38.3269, -109.8783, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Capitol Reef", "🏜️", 38.3670, -111.2615, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Mount Rushmore", "🗿", 43.8791, -103.4591, LandmarkType.SCENIC_LANDMARK, 2),
        LandmarkPoint("Badlands", "🏜️", 43.8554, -102.3397, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Glacier Nat'l Park", "🏔️", 48.7596, -113.7870, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Great Salt Lake", "🌊", 41.1158, -112.4768, LandmarkType.NATURAL_WONDER, 2),
        LandmarkPoint("Bonneville Salt Flats", "🏎️", 40.7997, -113.8000, LandmarkType.SCENIC_LANDMARK, 2),
        LandmarkPoint("Red Rock Canyon", "🏜️", 36.1354, -115.4272, LandmarkType.SCENIC_LANDMARK, 2),
        LandmarkPoint("Niagara Falls", "🌊", 43.0962, -79.0377, LandmarkType.NATURAL_WONDER, 1),
        LandmarkPoint("Pikes Peak", "🏔️", 38.8405, -105.0442, LandmarkType.NATURAL_WONDER, 2),
        LandmarkPoint("Garden of the Gods", "🪨", 38.8784, -104.8698, LandmarkType.SCENIC_LANDMARK, 2),
        LandmarkPoint("Crater Lake", "🌋", 42.9446, -122.1090, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Mount Rainier", "🏔️", 46.8523, -121.7603, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Olympic Nat'l Park", "🌲", 47.8021, -123.6044, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Joshua Tree", "🌵", 33.8734, -115.9010, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Sequoia Nat'l Park", "🌲", 36.4864, -118.5658, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Kings Canyon", "🌲", 36.8879, -118.5551, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Carlsbad Caverns", "🦇", 32.1753, -104.4439, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("White Sands", "🏖️", 32.7872, -106.3257, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Saguaro Nat'l Park", "🌵", 32.2967, -111.1666, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Acadia Nat'l Park", "🌊", 44.3386, -68.2733, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Shenandoah", "🌲", 38.2928, -78.6796, LandmarkType.NATIONAL_PARK, 2),
        LandmarkPoint("Great Smoky Mtns", "⛰️", 35.6118, -83.4895, LandmarkType.NATIONAL_PARK, 1),
        LandmarkPoint("Everglades", "🐊", 25.2866, -80.8987, LandmarkType.NATIONAL_PARK, 2)
    )

    // Comprehensive offline regional and global cities catalog (P1-03, P1-04A)
    val MAJOR_CITIES: List<CityPoint> = listOf(
        // North America - Northeast & Mid-Atlantic Corridor (e.g. "east" Trip)
        CityPoint("New York", 40.7128, -74.0060, "US", 1),
        CityPoint("Brooklyn", 40.6782, -73.9442, "US", 2),
        CityPoint("Queens", 40.7282, -73.7949, "US", 2),
        CityPoint("Jersey City", 40.7178, -74.0431, "US", 2),
        CityPoint("Newark", 40.7357, -74.1724, "US", 2),
        CityPoint("White Plains", 41.0339, -73.7629, "US", 3),
        CityPoint("Yonkers", 40.9312, -73.8987, "US", 3),
        CityPoint("Stamford", 41.0534, -73.5387, "US", 3),
        CityPoint("New Haven", 41.3083, -72.9279, "US", 2),
        CityPoint("Hartford", 41.7658, -72.6734, "US", 2),
        CityPoint("Waterbury", 41.5582, -73.0515, "US", 3),
        CityPoint("Providence", 41.8240, -71.4128, "US", 2),
        CityPoint("Newport", 41.4901, -71.3128, "US", 3),
        CityPoint("Boston", 42.3601, -71.0589, "US", 1),
        CityPoint("Cambridge", 42.3736, -71.1097, "US", 2),
        CityPoint("Lowell", 42.6334, -71.3162, "US", 3),
        CityPoint("Salem", 42.5195, -70.8967, "US", 3),
        CityPoint("Worcester", 42.2626, -71.8023, "US", 2),
        CityPoint("Springfield", 42.1015, -72.5898, "US", 3),
        CityPoint("Manchester", 42.9956, -71.4548, "US", 3),
        CityPoint("Portsmouth", 43.0718, -70.7626, "US", 3),
        CityPoint("Portland", 43.6591, -70.2568, "US", 2),
        CityPoint("Burlington", 44.4759, -73.2121, "US", 3),
        CityPoint("Albany", 42.6526, -73.7562, "US", 2),
        CityPoint("Schenectady", 42.8142, -73.9396, "US", 3),
        CityPoint("Saratoga Springs", 43.0831, -73.7846, "US", 3),
        CityPoint("Kingston", 41.9270, -73.9974, "US", 3),
        CityPoint("Poughkeepsie", 41.7004, -73.9210, "US", 3),
        CityPoint("Newburgh", 41.5034, -74.0104, "US", 3),
        CityPoint("Utica", 43.1009, -75.2327, "US", 3),
        CityPoint("Syracuse", 43.0481, -76.1474, "US", 2),
        CityPoint("Ithaca", 42.4440, -76.5019, "US", 3),
        CityPoint("Binghamton", 42.0987, -75.9180, "US", 3),
        CityPoint("Rochester", 43.1566, -77.6088, "US", 2),
        CityPoint("Buffalo", 42.8864, -78.8784, "US", 1),
        CityPoint("Niagara Falls", 43.0962, -79.0377, "US", 2),
        CityPoint("Philadelphia", 39.9526, -75.1652, "US", 1),
        CityPoint("Allentown", 40.6084, -75.4902, "US", 3),
        CityPoint("Scranton", 41.4090, -75.6624, "US", 3),
        CityPoint("Harrisburg", 40.2732, -76.8867, "US", 3),
        CityPoint("Lancaster", 40.0379, -76.3055, "US", 3),
        CityPoint("Trenton", 40.2171, -74.7429, "US", 3),
        CityPoint("Princeton", 40.3573, -74.6672, "US", 3),
        CityPoint("Atlantic City", 39.3643, -74.4229, "US", 3),
        CityPoint("Wilmington", 39.7447, -75.5484, "US", 3),
        CityPoint("Baltimore", 39.2904, -76.6122, "US", 1),
        CityPoint("Annapolis", 38.9784, -76.4922, "US", 3),
        CityPoint("Washington DC", 38.9072, -77.0369, "US", 1),
        CityPoint("Alexandria", 38.8048, -77.0469, "US", 3),
        CityPoint("Richmond", 37.5407, -77.4360, "US", 2),
        CityPoint("Norfolk", 36.8508, -76.2859, "US", 3),
        CityPoint("Charlottesville", 38.0293, -78.4767, "US", 3),
        CityPoint("Pittsburgh", 40.4406, -79.9959, "US", 1),
        CityPoint("Erie", 42.1292, -80.0851, "US", 3),

        // Canada - Ontario & Quebec
        CityPoint("Toronto", 43.6532, -79.3832, "CA", 1),
        CityPoint("Mississauga", 43.5890, -79.6441, "CA", 2),
        CityPoint("Hamilton", 43.2557, -79.8711, "CA", 2),
        CityPoint("Niagara-on-the-Lake", 43.2550, -79.0773, "CA", 3),
        CityPoint("St. Catharines", 43.1594, -79.2469, "CA", 3),
        CityPoint("Kitchener", 43.4516, -80.4925, "CA", 3),
        CityPoint("London", 42.9849, -81.2453, "CA", 2),
        CityPoint("Windsor", 42.3149, -83.0364, "CA", 3),
        CityPoint("Kingston", 44.2312, -76.4860, "CA", 3),
        CityPoint("Ottawa", 45.4215, -75.6972, "CA", 1),
        CityPoint("Gatineau", 45.4765, -75.7013, "CA", 3),
        CityPoint("Montreal", 45.5017, -73.5673, "CA", 1),
        CityPoint("Laval", 45.6066, -73.7124, "CA", 3),
        CityPoint("Quebec City", 46.8139, -71.2080, "CA", 2),

        // North America - Midwest & South
        CityPoint("Cleveland", 41.4993, -81.6944, "US", 2),
        CityPoint("Columbus", 39.9612, -82.9988, "US", 2),
        CityPoint("Cincinnati", 39.1031, -84.5120, "US", 2),
        CityPoint("Detroit", 42.3314, -83.0458, "US", 1),
        CityPoint("Ann Arbor", 42.2808, -83.7430, "US", 3),
        CityPoint("Grand Rapids", 42.9634, -85.6681, "US", 3),
        CityPoint("Chicago", 41.8781, -87.6298, "US", 1),
        CityPoint("Milwaukee", 43.0389, -87.9065, "US", 2),
        CityPoint("Indianapolis", 39.7684, -86.1581, "US", 2),
        CityPoint("Louisville", 38.2527, -85.7585, "US", 2),
        CityPoint("Nashville", 36.1627, -86.7816, "US", 2),
        CityPoint("Memphis", 35.1495, -90.0490, "US", 2),
        CityPoint("St. Louis", 38.6270, -90.1994, "US", 2),
        CityPoint("Minneapolis", 44.9778, -93.2650, "US", 2),
        CityPoint("Atlanta", 33.7490, -84.3880, "US", 1),
        CityPoint("Charlotte", 35.2271, -80.8431, "US", 2),
        CityPoint("Raleigh", 35.7796, -78.6382, "US", 2),
        CityPoint("Miami", 25.7617, -80.1918, "US", 1),
        CityPoint("Orlando", 28.5383, -81.3792, "US", 2),
        CityPoint("Tampa", 27.9506, -82.4572, "US", 2),
        CityPoint("New Orleans", 29.9511, -90.0715, "US", 2),
        CityPoint("Houston", 29.7604, -95.3698, "US", 1),
        CityPoint("Dallas", 32.7767, -96.7970, "US", 1),
        CityPoint("Austin", 30.2672, -97.7431, "US", 2),
        CityPoint("San Antonio", 29.4241, -98.4936, "US", 2),

        // North America - West, Inland & Transit Corridors (I-80, I-70, I-15, I-40)
        CityPoint("Denver", 39.7392, -104.9903, "US", 1),
        CityPoint("Colorado Springs", 38.8339, -104.8214, "US", 2),
        CityPoint("Boulder", 40.0150, -105.2705, "US", 2),
        CityPoint("Fort Collins", 40.5853, -105.0844, "US", 2),
        CityPoint("Grand Junction", 39.0639, -108.5506, "US", 2),
        CityPoint("Aspen", 39.1911, -106.8175, "US", 3),
        CityPoint("Vail", 39.6403, -106.3742, "US", 3),
        CityPoint("Pueblo", 38.2544, -104.6091, "US", 3),
        CityPoint("Salt Lake City", 40.7608, -111.8910, "US", 2),
        CityPoint("Provo", 40.2338, -111.6585, "US", 2),
        CityPoint("Ogden", 41.2230, -111.9738, "US", 2),
        CityPoint("Park City", 40.6461, -111.4980, "US", 3),
        CityPoint("St. George", 37.0965, -113.5684, "US", 2),
        CityPoint("Cedar City", 37.6775, -113.0619, "US", 3),
        CityPoint("Moab", 38.5733, -109.5498, "US", 2),
        CityPoint("Phoenix", 33.4484, -112.0740, "US", 1),
        CityPoint("Tucson", 32.2226, -110.9747, "US", 2),
        CityPoint("Flagstaff", 35.1983, -111.6513, "US", 2),
        CityPoint("Page", 36.9147, -111.4558, "US", 3),
        CityPoint("Kingman", 35.1894, -114.0530, "US", 3),
        CityPoint("Williams", 35.2495, -112.1910, "US", 3),
        CityPoint("Grand Canyon Village", 36.0544, -112.1401, "US", 3),
        CityPoint("Las Vegas", 36.1699, -115.1398, "US", 1),
        CityPoint("Henderson", 36.0395, -114.9817, "US", 2),
        CityPoint("Boulder City", 35.9786, -114.8325, "US", 3),
        CityPoint("Mesquite", 36.8055, -114.0672, "US", 3),
        CityPoint("Reno", 39.5296, -119.8138, "US", 2),
        CityPoint("Carson City", 39.1638, -119.7674, "US", 3),
        CityPoint("Elko", 40.8324, -115.7631, "US", 3),
        CityPoint("Ely", 39.2483, -114.8878, "US", 3),
        CityPoint("Cheyenne", 41.1400, -104.8202, "US", 2),
        CityPoint("Jackson", 43.4799, -110.7624, "US", 3),
        CityPoint("Casper", 42.8501, -106.3252, "US", 3),
        CityPoint("Laramie", 41.3114, -105.5911, "US", 3),
        CityPoint("Cody", 44.5263, -109.0565, "US", 3),
        CityPoint("Boise", 43.6150, -116.2023, "US", 2),
        CityPoint("Idaho Falls", 43.4927, -112.0401, "US", 3),
        CityPoint("Twin Falls", 42.5629, -114.4609, "US", 3),
        CityPoint("Pocatello", 42.8713, -112.4455, "US", 3),
        CityPoint("Billings", 45.7833, -108.5007, "US", 2),
        CityPoint("Bozeman", 45.6770, -111.0429, "US", 3),
        CityPoint("Missoula", 46.8721, -113.9940, "US", 3),
        CityPoint("Helena", 46.5958, -112.0363, "US", 3),
        CityPoint("Omaha", 41.2565, -95.9345, "US", 2),
        CityPoint("Lincoln", 40.8136, -96.7026, "US", 2),
        CityPoint("North Platte", 41.1239, -100.7654, "US", 3),
        CityPoint("Grand Island", 40.9264, -98.3420, "US", 3),
        CityPoint("Des Moines", 41.5868, -93.6250, "US", 2),
        CityPoint("Iowa City", 41.6611, -91.5302, "US", 3),
        CityPoint("Davenport", 41.5236, -90.5776, "US", 3),
        CityPoint("Kansas City", 39.0997, -94.5786, "US", 1),
        CityPoint("Wichita", 37.6872, -97.3301, "US", 2),
        CityPoint("Topeka", 39.0473, -95.6752, "US", 3),
        CityPoint("Albuquerque", 35.0844, -106.6504, "US", 2),
        CityPoint("Santa Fe", 35.6870, -105.9378, "US", 2),
        CityPoint("Gallup", 35.5281, -108.7426, "US", 3),
        CityPoint("Taos", 36.4072, -105.5734, "US", 3),
        CityPoint("Las Cruces", 32.3199, -106.7637, "US", 3),
        CityPoint("Los Angeles", 34.0522, -118.2437, "US", 1),
        CityPoint("San Diego", 32.7157, -117.1611, "US", 1),
        CityPoint("San Francisco", 37.7749, -122.4194, "US", 1),
        CityPoint("San Jose", 37.3382, -121.8863, "US", 2),
        CityPoint("Portland", 45.5152, -122.6784, "US", 1),
        CityPoint("Seattle", 47.6062, -122.3321, "US", 1),
        CityPoint("Vancouver", 49.2827, -123.1207, "CA", 1),
        CityPoint("Calgary", 51.0447, -114.0719, "CA", 2),
        CityPoint("Honolulu", 21.3069, -157.8583, "US", 1),
        CityPoint("Anchorage", 61.2181, -149.9003, "US", 2),
        CityPoint("Mexico City", 19.4326, -99.1332, "MX", 1),
        CityPoint("Cancun", 21.1619, -86.8515, "MX", 2),

        // Asia
        CityPoint("Seoul", 37.5665, 126.9780, "KR", 1),
        CityPoint("Incheon", 37.4563, 126.7052, "KR", 2),
        CityPoint("Busan", 35.1796, 129.0756, "KR", 1),
        CityPoint("Jeju", 33.4996, 126.5312, "KR", 2),
        CityPoint("Tokyo", 35.6762, 139.6503, "JP", 1),
        CityPoint("Yokohama", 35.4437, 139.6380, "JP", 2),
        CityPoint("Kyoto", 35.0116, 135.7681, "JP", 1),
        CityPoint("Osaka", 34.6937, 135.5023, "JP", 1),
        CityPoint("Fukuoka", 33.5904, 130.4017, "JP", 2),
        CityPoint("Sapporo", 43.0618, 141.3545, "JP", 2),
        CityPoint("Beijing", 39.9042, 116.4074, "CN", 1),
        CityPoint("Shanghai", 31.2304, 121.4737, "CN", 1),
        CityPoint("Hong Kong", 22.3193, 114.1694, "HK", 1),
        CityPoint("Taipei", 25.0330, 121.5654, "TW", 1),
        CityPoint("Singapore", 1.3521, 103.8198, "SG", 1),
        CityPoint("Bangkok", 13.7563, 100.5018, "TH", 1),
        CityPoint("Kuala Lumpur", 3.1390, 101.6869, "MY", 1),
        CityPoint("Hanoi", 21.0285, 105.8542, "VN", 1),
        CityPoint("Ho Chi Minh City", 10.8231, 106.6297, "VN", 1),
        CityPoint("Jakarta", -6.2088, 106.8456, "ID", 1),
        CityPoint("Manila", 14.5995, 120.9842, "PH", 1),
        CityPoint("Mumbai", 19.0760, 72.8777, "IN", 1),
        CityPoint("Delhi", 28.6139, 77.2090, "IN", 1),
        CityPoint("Dubai", 25.2048, 55.2708, "AE", 1),

        // Europe
        CityPoint("London", 51.5074, -0.1278, "GB", 1),
        CityPoint("Edinburgh", 55.9533, -3.1883, "GB", 2),
        CityPoint("Dublin", 53.3498, -6.2603, "IE", 1),
        CityPoint("Paris", 48.8566, 2.3522, "FR", 1),
        CityPoint("Nice", 43.7102, 7.2620, "FR", 2),
        CityPoint("Amsterdam", 52.3676, 4.9041, "NL", 1),
        CityPoint("Brussels", 50.8503, 4.3517, "BE", 1),
        CityPoint("Frankfurt", 50.1109, 8.6821, "DE", 1),
        CityPoint("Berlin", 52.5200, 13.4050, "DE", 1),
        CityPoint("Munich", 48.1351, 11.5820, "DE", 1),
        CityPoint("Zurich", 47.3769, 8.5417, "CH", 1),
        CityPoint("Geneva", 46.2044, 6.1432, "CH", 2),
        CityPoint("Vienna", 48.2082, 16.3738, "AT", 1),
        CityPoint("Rome", 41.9028, 12.4964, "IT", 1),
        CityPoint("Milan", 45.4642, 9.1900, "IT", 1),
        CityPoint("Florence", 43.7696, 11.2558, "IT", 2),
        CityPoint("Venice", 45.4408, 12.3155, "IT", 2),
        CityPoint("Madrid", 40.4168, -3.7038, "ES", 1),
        CityPoint("Barcelona", 41.3851, 2.1734, "ES", 1),
        CityPoint("Lisbon", 38.7223, -9.1393, "PT", 1),
        CityPoint("Prague", 50.0755, 14.4378, "CZ", 1),
        CityPoint("Budapest", 47.4979, 19.0402, "HU", 1),
        CityPoint("Copenhagen", 55.6761, 12.5683, "DK", 1),
        CityPoint("Stockholm", 59.3293, 18.0686, "SE", 1),
        CityPoint("Oslo", 59.9139, 10.7522, "NO", 1),
        CityPoint("Helsinki", 60.1699, 24.9384, "FI", 1),
        CityPoint("Athens", 37.9838, 23.7275, "GR", 1),
        CityPoint("Istanbul", 41.0082, 28.9784, "TR", 1),

        // Oceania, South America, Africa
        CityPoint("Sydney", -33.8688, 151.2093, "AU", 1),
        CityPoint("Melbourne", -37.8136, 144.9631, "AU", 1),
        CityPoint("Brisbane", -27.4698, 153.0251, "AU", 2),
        CityPoint("Auckland", -36.8485, 174.7633, "NZ", 1),
        CityPoint("Sao Paulo", -23.5505, -46.6333, "BR", 1),
        CityPoint("Rio de Janeiro", -22.9068, -43.1729, "BR", 1),
        CityPoint("Buenos Aires", -34.6037, -58.3816, "AR", 1),
        CityPoint("Santiago", -33.4489, -70.6693, "CL", 1),
        CityPoint("Lima", -12.0464, -77.0428, "PE", 1),
        CityPoint("Bogota", 4.7110, -74.0721, "CO", 1),
        CityPoint("Cairo", 30.0444, 31.2357, "EG", 1),
        CityPoint("Cape Town", -33.9249, 18.4241, "ZA", 1),
        CityPoint("Johannesburg", -26.2041, 28.0473, "ZA", 1)
    )

    /**
     * Resolves the nearest major city/region within [maxDistanceKm] (default 60km).
     * Returns null if no known major city is within range.
     */
    fun resolveNearestCity(location: GeoPoint, maxDistanceKm: Double = 60.0): CityPoint? {
        var closestCity: CityPoint? = null
        var minDistanceMeters = maxDistanceKm * 1000.0

        for (city in MAJOR_CITIES) {
            val dist = GeodesicUtils.distanceMeters(
                location,
                GeoPoint(city.latitude, city.longitude)
            )
            if (dist < minDistanceMeters) {
                minDistanceMeters = dist
                closestCity = city
            }
        }

        return closestCity
    }

    /**
     * Resolves the nearest iconic landmark or national park within [maxDistanceKm] (default 40km).
     */
    fun resolveNearestLandmark(location: GeoPoint, maxDistanceKm: Double = 40.0): LandmarkPoint? {
        var closestLandmark: LandmarkPoint? = null
        var minDistanceMeters = maxDistanceKm * 1000.0

        for (landmark in MAJOR_LANDMARKS) {
            val dist = GeodesicUtils.distanceMeters(
                location,
                GeoPoint(landmark.latitude, landmark.longitude)
            )
            if (dist < minDistanceMeters) {
                minDistanceMeters = dist
                closestLandmark = landmark
            }
        }

        return closestLandmark
    }

    /**
     * Returns a human-friendly place name for a visit (P1-04).
     * Normalizes raw semantic enums (INFERRED_HOME, INFERRED_WORK) and eliminates raw "UNKNOWN" labels.
     */
    fun getEffectivePlaceName(
        rawPlaceName: String?,
        isUserOverride: Boolean,
        location: GeoPoint,
        placeAddress: String? = null
    ): EffectivePlaceLabel {
        val trimmed = rawPlaceName?.trim()

        // 1. Semantic normalization: INFERRED_HOME / HOME
        if (trimmed != null && (trimmed.equals("INFERRED_HOME", ignoreCase = true) || trimmed.equals("HOME", ignoreCase = true))) {
            val nearest = resolveNearestCity(location, maxDistanceKm = 60.0)
            val displayName = if (nearest != null) "Home · ${nearest.name}" else "Home"
            return EffectivePlaceLabel(
                displayName = displayName,
                shouldShowOnMap = true,
                isCityFallback = false
            )
        }

        // 2. Semantic normalization: INFERRED_WORK / WORK
        if (trimmed != null && (trimmed.equals("INFERRED_WORK", ignoreCase = true) || trimmed.equals("WORK", ignoreCase = true))) {
            val nearest = resolveNearestCity(location, maxDistanceKm = 60.0)
            val displayName = if (nearest != null) "Work · ${nearest.name}" else "Work"
            return EffectivePlaceLabel(
                displayName = displayName,
                shouldShowOnMap = true,
                isCityFallback = false
            )
        }

        // 3. Genuine non-blank place name
        if (!trimmed.isNullOrBlank() && !trimmed.equals("UNKNOWN", ignoreCase = true) && !trimmed.equals("null", ignoreCase = true)) {
            return EffectivePlaceLabel(
                displayName = trimmed,
                shouldShowOnMap = true,
                isCityFallback = false
            )
        }

        // 4. Locality / Address fallback if available
        if (!placeAddress.isNullOrBlank()) {
            val firstSegment = placeAddress.split(",").firstOrNull()?.trim()
            if (!firstSegment.isNullOrBlank() && !firstSegment.all { it.isDigit() }) {
                return EffectivePlaceLabel(
                    displayName = firstSegment,
                    shouldShowOnMap = true,
                    isCityFallback = false
                )
            }
        }

        // 4B & 5. Offline nearest landmark or city fallback
        val nearestLandmark = resolveNearestLandmark(location, maxDistanceKm = 40.0)
        val nearestCity = resolveNearestCity(location, maxDistanceKm = 60.0)

        val distToLandmark = nearestLandmark?.let {
            GeodesicUtils.distanceMeters(location, GeoPoint(it.latitude, it.longitude))
        } ?: Double.MAX_VALUE

        val distToCity = nearestCity?.let {
            GeodesicUtils.distanceMeters(location, GeoPoint(it.latitude, it.longitude))
        } ?: Double.MAX_VALUE

        // If closer to a landmark (or within 15km of a national park / scenic wonder), prioritize landmark
        if (nearestLandmark != null && (distToLandmark < distToCity || distToLandmark < 15_000.0)) {
            return EffectivePlaceLabel(
                displayName = "${nearestLandmark.icon} ${nearestLandmark.name}",
                shouldShowOnMap = true,
                isCityFallback = true
            )
        }

        if (nearestCity != null) {
            return EffectivePlaceLabel(
                displayName = "Near ${nearestCity.name}",
                shouldShowOnMap = true,
                isCityFallback = true
            )
        }

        // 6. Neutral unlabeled stop: Omit text label on map, show friendly label in diary
        return EffectivePlaceLabel(
            displayName = "Unlabeled stop",
            shouldShowOnMap = false,
            isCityFallback = false
        )
    }
}

data class EffectivePlaceLabel(
    val displayName: String,
    val shouldShowOnMap: Boolean,
    val isCityFallback: Boolean
)
