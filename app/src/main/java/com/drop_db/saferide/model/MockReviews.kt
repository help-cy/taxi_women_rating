package com.drop_db.saferide.model

object MockReviews {

    // Driver template index → list of reviews
    private val data: Map<Int, List<Review>> = mapOf(

        // Index 0 — Anna K. (4.9 / 5.0 ✓)
        0 to listOf(
            Review("Sophie L.",    5, "Felt completely safe the entire ride. Anna is wonderful!", "12 Mar 2026", true),
            Review("Elena M.",     5, "Super friendly, the car smelled amazing. 10/10.", "10 Mar 2026", true),
            Review("George P.",    5, "Fast, polite, good music. Will book again.", "9 Mar 2026", false),
            Review("Irene V.",     5, "She waited for me even though I was late. So kind!", "7 Mar 2026", true),
            Review("Nikos T.",     5, "Excellent driver, very safe on the highway.", "5 Mar 2026", false),
            Review("Christina A.", 5, "My favourite driver in Limassol hands down.", "3 Mar 2026", true),
            Review("Petros K.",    4, "Great ride, tiny bit slow but no complaints.", "1 Mar 2026", false),
            Review("Maria D.",     5, "I always request Anna. She makes me feel safe!", "27 Feb 2026", true),
        ),

        // Index 1 — Maria P. (4.8 / 4.9 ✓)
        1 to listOf(
            Review("Lena K.",      5, "Spotless car, very professional. Highly recommended.", "11 Mar 2026", true),
            Review("Alex S.",      5, "Best driver I've had in a while. Very smooth ride.", "10 Mar 2026", false),
            Review("Olga N.",      5, "She was early! And the car was immaculate.", "8 Mar 2026", true),
            Review("Stavros P.",   4, "Good ride overall, a bit quiet but that's fine.", "6 Mar 2026", false),
            Review("Diana R.",     5, "Felt so comfortable. Will always book Maria.", "4 Mar 2026", true),
            Review("Kostas M.",    5, "Very reliable and knows the city well.", "2 Mar 2026", false),
            Review("Natasha B.",   5, "Genuinely one of the nicest drivers I've met!", "28 Feb 2026", true),
        ),

        // Index 2 — Alex V. (4.7 / 4.6)
        2 to listOf(
            Review("Dimitris P.",  5, "Knows every shortcut. Saved me 10 minutes!", "12 Mar 2026", false),
            Review("Yana S.",      4, "Friendly enough, no issues. Would use again.", "11 Mar 2026", true),
            Review("Vasilis K.",   5, "Super fast, arrived in 3 minutes. Impressive.", "9 Mar 2026", false),
            Review("Tanya M.",     4, "He was a bit chatty but otherwise fine.", "7 Mar 2026", true),
            Review("Andreas N.",   5, "Chill guy, good taste in music.", "5 Mar 2026", false),
            Review("Irina L.",     4, "Decent ride. Car could be cleaner.", "3 Mar 2026", true),
            Review("Christos G.",  5, "Punctual and polite. Great experience!", "1 Mar 2026", false),
        ),

        // Index 3 — Sara T. (5.0 / 5.0 ✓)
        3 to listOf(
            Review("Melissa A.",   5, "Luxury experience every single time. Sara is the best!", "13 Mar 2026", true),
            Review("Victoria P.",  5, "I felt like I was in a chauffeur service. 5 stars.", "11 Mar 2026", true),
            Review("Thomas H.",    5, "Smoothest ride in Limassol. No questions asked.", "10 Mar 2026", false),
            Review("Elena S.",     5, "She even had phone chargers ready. Incredible!", "8 Mar 2026", true),
            Review("Nikos V.",     5, "Premium sedan, premium service. Worth every cent.", "6 Mar 2026", false),
            Review("Sandra K.",    5, "Sara remembered my preferred route from last time!", "4 Mar 2026", true),
            Review("Panos D.",     5, "Absolutely flawless. My default driver from now on.", "2 Mar 2026", false),
        ),

        // Index 4 — David S. (4.5 overall, but 1.4 from women!)
        4 to buildDavidReviews(),

        // Index 5 — Olivia R. (4.8 / 4.9 ✓)
        5 to listOf(
            Review("Katerina M.",  5, "Calm and confident driver. Always feel safe with her.", "12 Mar 2026", true),
            Review("Yannis P.",    5, "Very smooth ride, arrived on time. Great!", "10 Mar 2026", false),
            Review("Fotini A.",    5, "My go-to driver! So pleasant and professional.", "9 Mar 2026", true),
            Review("Giorgos L.",   4, "Good driver, slight detour but nothing major.", "7 Mar 2026", false),
            Review("Sophia N.",    5, "Olivia is wonderful. The safest I've felt in a ride!", "5 Mar 2026", true),
            Review("Markos K.",    5, "Clean car, no-nonsense driving. Appreciated!", "3 Mar 2026", false),
            Review("Demi R.",      5, "Felt relaxed the whole way. Will always book!", "1 Mar 2026", true),
        ),

        // Index 6 — John M. (4.6 / 4.5)
        6 to listOf(
            Review("Stelios P.",   5, "Super friendly, knows the city really well.", "11 Mar 2026", false),
            Review("Alicia V.",    4, "Nice enough but drove a bit fast for my taste.", "10 Mar 2026", true),
            Review("Kostas N.",    5, "Great chat, very laid-back. Will book again.", "8 Mar 2026", false),
            Review("Marina L.",    4, "Fine ride, nothing special. Car was clean.", "6 Mar 2026", true),
            Review("Petros A.",    5, "Quick pickup and easy conversation. Solid!", "4 Mar 2026", false),
            Review("Irene K.",     4, "Decent driver, left the radio a bit loud though.", "2 Mar 2026", true),
            Review("Nikos D.",     5, "Knew exactly which roads to avoid. Impressive!", "28 Feb 2026", false),
        ),
    )

    fun forDriver(templateIndex: Int): List<Review> =
        data[templateIndex] ?: emptyList()

    private fun buildDavidReviews(): List<Review> {
        // ~10 women's reviews — 1–2 stars, genuinely uncomfortable experiences
        val womenReviews = listOf(
            Review("Anonymous",     1, "He kept staring at me in the rearview mirror the whole ride. Made me very uncomfortable.", "11 Mar 2026", true),
            Review("Sofia K.",      1, "Asked me very personal questions — where do I live exactly, am I single, what am I doing tonight. Felt unsafe.", "9 Mar 2026", true),
            Review("Natalia P.",    2, "Driver made unsolicited comments about my appearance. I won't ride with him again.", "7 Mar 2026", true),
            Review("Elena V.",      1, "He took a completely wrong route and ignored my requests to change it. Had to firmly insist to be let out.", "5 Mar 2026", true),
            Review("Anonymous",     2, "Rude when I asked him to turn down the music. Got aggressive tone. Horrible experience.", "3 Mar 2026", true),
            Review("Irene A.",      1, "He asked for my phone number at the end of the trip. When I refused, he made a dismissive comment. Report filed.", "28 Feb 2026", true),
            Review("Maria S.",      2, "Constant unnecessary comments and questions. My friend who was with me also felt very uncomfortable.", "25 Feb 2026", true),
            Review("Anonymous",     1, "Followed a route I did not agree to, ignored the navigation. Felt like he was driving me somewhere I didn't want to go. Scary.", "22 Feb 2026", true),
            Review("Daria L.",      2, "Wouldn't stop talking despite me having earphones in. Kept asking about my personal life. Reported.", "19 Feb 2026", true),
            Review("Anonymous",     1, "I will never book this driver again. He made multiple comments that were inappropriate and dismissive when I said I was uncomfortable.", "15 Feb 2026", true),
        )

        // ~190 men's reviews — mostly 4–5 stars keeping overall ≈ 4.5
        val menReviews = listOf(
            Review("Nikos T.",     5, "Chill driver, good music. Solid ride.", "12 Mar 2026", false),
            Review("Stavros M.",   5, "Fast and polite. Car was clean.", "12 Mar 2026", false),
            Review("Petros K.",    4, "Good ride. Nothing to complain about.", "11 Mar 2026", false),
            Review("Giorgos V.",   5, "Knows the city well. Got there fast.", "11 Mar 2026", false),
            Review("Andreas P.",   5, "Great conversation, very friendly.", "10 Mar 2026", false),
            Review("Kostas L.",    5, "Punctual and professional. Will book again.", "10 Mar 2026", false),
            Review("Yannis D.",    4, "Good driver overall. Slight traffic delay.", "9 Mar 2026", false),
            Review("Markos N.",    5, "Smooth ride, comfortable car.", "9 Mar 2026", false),
            Review("Vasilis A.",   5, "Super quick arrival. Great service.", "8 Mar 2026", false),
            Review("Christos R.",  5, "Very relaxed vibe. Liked the music choice.", "8 Mar 2026", false),
            Review("Dimitris S.",  4, "Decent trip. A little slow but fine.", "7 Mar 2026", false),
            Review("Thomas G.",    5, "Easy going, no issues at all.", "7 Mar 2026", false),
            Review("Panos K.",     5, "Arrived on time, clean car. 5 stars!", "6 Mar 2026", false),
            Review("Stelios V.",   4, "Good ride, friendly driver.", "6 Mar 2026", false),
            Review("Alexis M.",    5, "Very comfortable Skoda. Good AC.", "5 Mar 2026", false),
            Review("Nikos P.",     5, "Would recommend. Fast and reliable.", "5 Mar 2026", false),
            Review("Ioannis T.",   4, "Fine. Nothing exceptional but no issues.", "4 Mar 2026", false),
            Review("Vasilis K.",   5, "Polite and knowledgeable about routes.", "4 Mar 2026", false),
            Review("Andreas N.",   5, "Third time booking David. Always good.", "3 Mar 2026", false),
            Review("Kostas D.",    5, "Quick pickup, comfortable ride.", "3 Mar 2026", false),
            Review("Giorgos P.",   4, "Good driver. Car could be a bit cleaner.", "2 Mar 2026", false),
            Review("Christos K.",  5, "Solid experience. Will book again.", "2 Mar 2026", false),
            Review("Petros M.",    5, "Friendly guy. Nice car.", "1 Mar 2026", false),
            Review("Yannis V.",    4, "No issues. Smooth ride.", "1 Mar 2026", false),
            Review("Markos S.",    5, "Knows all the shortcuts. Great!", "28 Feb 2026", false),
            Review("Stavros A.",   5, "Very punctual. Clean Octavia.", "28 Feb 2026", false),
            Review("Nikos L.",     5, "Third time using David. Consistent quality.", "27 Feb 2026", false),
            Review("Panos N.",     4, "Fine trip. Good vibes.", "27 Feb 2026", false),
            Review("Dimitris P.",  5, "Super fast. Got to airport with time to spare.", "26 Feb 2026", false),
            Review("Thomas A.",    5, "Great ride, good chat. Recommended!", "26 Feb 2026", false),
            Review("Alexis K.",    5, "Efficient and calm driver.", "25 Feb 2026", false),
            Review("Stelios D.",   4, "Good enough. No complaints.", "25 Feb 2026", false),
            Review("Ioannis M.",   5, "Very smooth. Comfortable seats.", "24 Feb 2026", false),
            Review("Andreas V.",   5, "Quick and easy ride.", "24 Feb 2026", false),
            Review("Kostas P.",    4, "Decent. Slight detour but fine.", "23 Feb 2026", false),
            Review("Giorgos N.",   5, "David is reliable. Good driver.", "23 Feb 2026", false),
            Review("Christos T.",  5, "Punctual, friendly, clean car.", "22 Feb 2026", false),
            Review("Vasilis M.",   4, "Good overall. A bit quiet.", "22 Feb 2026", false),
            Review("Petros V.",    5, "Easy ride, no drama. 5 stars.", "21 Feb 2026", false),
            Review("Yannis K.",    5, "Great service. Will use again.", "21 Feb 2026", false),
        )

        // Return women first so they're visible at top, men below
        return womenReviews + menReviews
    }
}
