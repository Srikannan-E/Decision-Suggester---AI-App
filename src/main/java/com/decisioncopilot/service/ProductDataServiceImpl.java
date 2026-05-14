package com.decisioncopilot.service;

import com.decisioncopilot.dto.ProductData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Simulated catalog in INR with model-style highlights (replace with real API / scraper later).
@Service
public class ProductDataServiceImpl implements ProductDataService {

    @Override
    public ProductData fetchProductData(String input, String category) {
        int hash = Math.abs(input.hashCode());
        String cat = category != null ? category.trim() : "general";
        String lower = input.toLowerCase(Locale.ROOT);

        BigDecimal rating = generateRating(hash);
        int reviewCount = generateReviewCount(hash);
        BigDecimal price = priceInr(lower, cat, hash, rating);
        Segment segment = inferSegment(lower, cat);

        String[] features = buildFeatureList(input, segment, hash, rating, reviewCount);
        String specSummary = buildSpecSummary(input, segment, price, rating, reviewCount, hash);

        return new ProductData(
            input,
            price,
            rating,
            reviewCount,
            cat,
            null,
            null,
            features,
            specSummary
        );
    }

    private enum Segment {
        SMARTPHONE, LAPTOP, AUDIO, HOME, FITNESS, FASHION, BOOKS, GENERAL
    }

    private Segment inferSegment(String lower, String category) {
        if (matches(lower, "iphone", "galaxy", "pixel", "poco", "redmi", "xiaomi", "oneplus", "realme", "oppo", "vivo", "nothing", "motorola", "5g", "phone")) {
            return Segment.SMARTPHONE;
        }
        if (matches(lower, "macbook", "laptop", "thinkpad", "ideapad", "chromebook", "ultrabook")) {
            return Segment.LAPTOP;
        }
        if (matches(lower, "headphone", "earbuds", "airpods", "wh-1000", "speaker", "soundbar", "anc ")) {
            return Segment.AUDIO;
        }
        if ("fitness".equalsIgnoreCase(category)) {
            return Segment.FITNESS;
        }
        if ("fashion".equalsIgnoreCase(category)) {
            return Segment.FASHION;
        }
        if ("books".equalsIgnoreCase(category)) {
            return Segment.BOOKS;
        }
        if ("home".equalsIgnoreCase(category) || matches(lower, "desk", "chair", "mixer", "induction", "vacuum")) {
            return Segment.HOME;
        }
        if ("electronics".equalsIgnoreCase(category)) {
            return Segment.GENERAL;
        }
        return Segment.GENERAL;
    }

    private static boolean matches(String lower, String... needles) {
        for (String n : needles) {
            if (lower.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal priceInr(String lower, String category, int hash, BigDecimal rating) {
        // Known budget-phone bands (still deterministic via hash)
        if (lower.contains("poco m4")) {
            return BigDecimal.valueOf(10_499 + (hash % 2_800)).setScale(0, RoundingMode.HALF_UP);
        }
        if (lower.contains("redmi note")) {
            return BigDecimal.valueOf(11_999 + (hash % 9_000)).setScale(0, RoundingMode.HALF_UP);
        }
        if (lower.contains("iphone")) {
            return BigDecimal.valueOf(52_999 + (hash % 85_000)).setScale(0, RoundingMode.HALF_UP);
        }

        int catHash = Math.abs(category.hashCode());
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "electronics" -> smartphoneBand(lower, hash, rating)
                .max(BigDecimal.valueOf(1_499 + (catHash % 4_000)))
                .setScale(0, RoundingMode.HALF_UP);
            case "home" -> BigDecimal.valueOf(899 + (hash % 42_000)).setScale(0, RoundingMode.HALF_UP);
            case "fitness" -> BigDecimal.valueOf(599 + (hash % 28_000)).setScale(0, RoundingMode.HALF_UP);
            case "fashion" -> BigDecimal.valueOf(499 + (hash % 18_000)).setScale(0, RoundingMode.HALF_UP);
            case "books" -> BigDecimal.valueOf(199 + (hash % 2_500)).setScale(0, RoundingMode.HALF_UP);
            default -> BigDecimal.valueOf(699 + (hash % 55_000)).setScale(0, RoundingMode.HALF_UP);
        };
    }

    private BigDecimal smartphoneBand(String lower, int hash, BigDecimal rating) {
        boolean budget = matches(lower, "poco", "redmi", "realme", "moto ", "infinix", "itel");
        boolean flagship = matches(lower, "iphone", "galaxy s", "pixel", "pro max", "ultra");
        if (flagship) {
            return BigDecimal.valueOf(45_999 + (hash % 95_000));
        }
        if (budget) {
            return BigDecimal.valueOf(7_999 + (hash % 14_000));
        }
        return BigDecimal.valueOf(14_999 + (hash % 35_000));
    }

    private BigDecimal generateRating(int hash) {
        double rating = 2.6 + (hash % 24) / 10.0;
        return BigDecimal.valueOf(Math.min(5.0, rating)).setScale(2, RoundingMode.HALF_UP);
    }

    private int generateReviewCount(int hash) {
        return 48 + (hash % 12_000);
    }

    private String[] buildFeatureList(String name, Segment segment, int hash, BigDecimal rating, int reviews) {
        List<String> pool = featurePool(segment, name);
        Set<String> picked = new LinkedHashSet<>();
        int i = hash;
        while (picked.size() < 6 && !pool.isEmpty()) {
            picked.add(pool.get(Math.floorMod(i, pool.size())));
            i += 7 + hash % 5;
        }
        if (rating.compareTo(BigDecimal.valueOf(4.2)) >= 0) {
            picked.add("Owner reviews skew positive in aggregate for this listing class (" + reviews + "+ ratings sampled).");
        }
        return picked.toArray(String[]::new);
    }

    private List<String> featurePool(Segment segment, String name) {
        String n = name.trim();
        List<String> list = new ArrayList<>();
        String lower = n.toLowerCase(Locale.ROOT);

        switch (segment) {
            case SMARTPHONE -> {
                if (lower.contains("poco m4")) {
                    list.addAll(List.of(
                        "MediaTek Dimensity 700 family SoC — day-to-day and 5G use in its class.",
                        "Large battery (typ. 5000 mAh class) with 18W fast charging on many regional SKUs.",
                        "90 Hz refresh display on supported variants — smoother scrolling than 60 Hz panels.",
                        "Dual/triple rear camera setup tuned for daylight social shots; low-light varies by firmware.",
                        "Side-mounted capacitive fingerprint reader and face unlock options.",
                        "MIUI / HyperOS feature set with customizable themes; ad placement varies by region/build."
                    ));
                } else {
                    list.addAll(List.of(
                        "5G / 4G connectivity depends on exact SKU and carrier band support in your region.",
                        "Display stack (LCD vs AMOLED, refresh rate) varies by sub-model — check the precise variant.",
                        "Primary + ultrawide camera pairing common; macro/depth sensors are often entry-grade.",
                        "UFS storage tier and RAM configuration strongly affect multitasking longevity.",
                        "Stereo speakers, IP rating, and wireless charging are segment-dependent — verify spec sheet.",
                        "Software update policy differs by OEM; budget lines may have shorter major OS support."
                    ));
                }
                if (lower.contains("5g")) {
                    list.add(0, "5G modem present on applicable SKUs — confirm bands against your operator.");
                }
            }
            case LAPTOP -> list.addAll(List.of(
                "CPU generation and TDP class drive sustained performance more than model name alone.",
                "RAM soldered vs SO-DIMM slots affects future upgrades — check before purchase.",
                "Display sRGB coverage and brightness matter for photo/video work outdoors.",
                "SSD form factor (NVMe Gen3/4) and thermals influence real-world speed under load.",
                "Battery watt-hour rating and USB-C charging power vary widely within the same series."
            ));
            case AUDIO -> list.addAll(List.of(
                "ANC depth and wind handling differ by firmware generation and ear-tip seal.",
                "Codec support (LDAC, aptX Adaptive, AAC) depends on phone + headset pairing.",
                "Multipoint and spatial audio features are often gated by app or OS ecosystem.",
                "Battery life claims are usually with ANC off — expect shorter runtime with ANC maxed."
            ));
            case HOME -> list.addAll(List.of(
                "Materials and warranty length often separate budget SKUs from premium lines.",
                "Assembly complexity and spare-part availability vary — check user manuals before buying.",
                "Power draw / safety certifications should match local voltage and plug type."
            ));
            case FITNESS -> list.addAll(List.of(
                "GPS accuracy and heart-rate sensor quality vary vs dedicated sports watches.",
                "Water resistance rating applies to fresh water only unless explicitly stated for swim tracking.",
                "Third-party app sync (Strava, Apple Health, Google Fit) depends on vendor integrations."
            ));
            case FASHION -> list.addAll(List.of(
                "Fabric composition (cotton vs blends) drives comfort and shrinkage on first wash.",
                "Size charts are brand-specific — compare flat measurements, not only letter sizes.",
                "Return policy for opened hygiene-related items may be restricted."
            ));
            case BOOKS -> list.addAll(List.of(
                "ISBN/edition determines pagination and any included digital extras.",
                "Print quality (paper gsm) and binding type affect longevity for reference use.",
                "Translations and regional abridgements can differ under the same marketing title."
            ));
            default -> list.addAll(List.of(
                "Cross-check the exact model / region code against the manufacturer's spec PDF.",
                "Warranty and authorized service coverage differ by import vs official channel.",
                "Accessory bundles in e-commerce listings may not match retail box contents."
            ));
        }
        return list;
    }

    private String buildSpecSummary(
        String name,
        Segment segment,
        BigDecimal priceInr,
        BigDecimal rating,
        int reviewCount,
        int hash) {
        String tier = priceInr.compareTo(BigDecimal.valueOf(25_000)) < 0 ? "budget-to-mid" : "upper-mid-to-premium";
        String reviewTone = rating.compareTo(BigDecimal.valueOf(4.0)) >= 0 ? "generally favorable" : "mixed";

        return switch (segment) {
            case SMARTPHONE -> String.format(
                "%s is treated here as a %s smartphone signal at an indicative ₹%s street band. "
                    + "Typical buyer focus: display type/refresh, SoC sustained performance, camera ISP tuning, "
                    + "and software update cadence. Aggregate simulated rating %.2f/5 from %d review datapoints — %s. "
                    + "Always confirm the exact RAM/storage SKU, regional bands, and warranty before checkout.",
                name, tier, priceInr.toPlainString(), rating, reviewCount, reviewTone
            );
            case LAPTOP -> String.format(
                "%s — %s laptop band around ₹%s (simulated). Thermal headroom, display panel lottery, "
                    + "and upgradeable RAM/SSD matter more than marketing model year alone. Rating %.2f/5, %d reviews (simulated).",
                name, tier, priceInr.toPlainString(), rating, reviewCount
            );
            case AUDIO -> String.format(
                "%s in the personal audio segment — indicative ₹%s. Comfort, codec support, and ANC tuning "
                    + "are the usual differentiators. Simulated aggregate %.2f/5 over %d ratings.",
                name, priceInr.toPlainString(), rating, reviewCount
            );
            default -> String.format(
                "%s (%s): indicative ₹%s with simulated %.2f/5 rating across %d datapoints — use this as a quick triage only.",
                name, segment.name().toLowerCase(Locale.ROOT), priceInr.toPlainString(), rating, reviewCount
            );
        };
    }
}
