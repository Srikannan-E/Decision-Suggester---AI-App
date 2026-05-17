package com.decisioncopilot.service;

import com.decisioncopilot.dto.LlmDecisionResult;
import com.decisioncopilot.dto.ProductData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FIXED: Pure local decision engine - NO API calls
 * Generates buying decisions based on simulated product data
 * Fast response (< 100ms) with no external dependencies
 */
@Service
public class LlmDecisionService {

    private static final Logger log = LoggerFactory.getLogger(LlmDecisionService.class);

    /**
     * FIXED: REMOVED @CircuitBreaker and @Retry annotations
     * These were trying to call external AI API which was BLOCKING forever
     * 
     * Now using pure local algorithm - instant response
     */
    public LlmDecisionResult generateDecision(ProductData product) {
        log.info("Generating decision for product: {}", product.name());

        try {
            int budgetRupee = parseRupeeBudget(product.buyerBudget());
            BigDecimal price = product.price();

            String verdict = pickVerdict(product.rating(), price, budgetRupee);
            BigDecimal confidence = confidenceFromSignals(product.rating(), product.reviewCount(), budgetRupee, price);

            String[] pros = buildPros(product, budgetRupee, price);
            String[] cons = buildCons(product, budgetRupee, price);

            String summary = buildSummary(product, verdict, budgetRupee, price);
            String reasoning = buildReasoning(product, budgetRupee);

            log.debug("Decision generated for {}: verdict={}, confidence={}", 
                product.name(), verdict, confidence);

            return new LlmDecisionResult(verdict, confidence, pros, cons, summary, reasoning);
        } catch (Exception e) {
            log.error("Error generating decision for product: {}", product.name(), e);
            // Return fallback decision on error
            return fallbackDecision(product, e);
        }
    }

    private static int parseRupeeBudget(String budget) {
        if (budget == null || budget.isBlank()) {
            return -1;
        }
        String digits = budget.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(digits.length() > 9 ? digits.substring(0, 9) : digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String pickVerdict(BigDecimal rating, BigDecimal price, int budgetRupee) {
        double r = rating.doubleValue();
        if (r < 2.9) {
            return "DONT_BUY";
        }
        if (r < 3.4) {
            return "MAYBE";
        }
        if (budgetRupee > 0) {
            BigDecimal b = BigDecimal.valueOf(budgetRupee);
            if (price.compareTo(b.multiply(BigDecimal.valueOf(1.12))) > 0) {
                return r >= 4.2 ? "MAYBE" : "DONT_BUY";
            }
        }
        if (r >= 4.15) {
            return "BUY";
        }
        if (r >= 3.55) {
            return "MAYBE";
        }
        return "MAYBE";
    }

    private static BigDecimal confidenceFromSignals(
        BigDecimal rating,
        int reviewCount,
        int budgetRupee,
        BigDecimal price) {
        double base = 0.48 + rating.doubleValue() * 0.09;
        if (reviewCount > 800) {
            base += 0.04;
        }
        if (reviewCount > 4000) {
            base += 0.03;
        }
        if (budgetRupee > 0) {
            BigDecimal b = BigDecimal.valueOf(budgetRupee);
            if (price.compareTo(b) <= 0) {
                base += 0.05;
            } else if (price.compareTo(b.multiply(BigDecimal.valueOf(1.15))) > 0) {
                base -= 0.08;
            }
        }
        return BigDecimal.valueOf(Math.min(0.93, Math.max(0.38, base))).setScale(2, RoundingMode.HALF_UP);
    }

    private static String[] buildPros(ProductData p, int budgetRupee, BigDecimal price) {
        Set<String> out = new LinkedHashSet<>();
        String[] fx = p.featureHighlights();
        if (fx != null) {
            for (int i = 0; i < fx.length && out.size() < 4; i++) {
                String s = shorten(fx[i], 140);
                if (!s.isBlank()) {
                    out.add(s);
                }
            }
        }
        if (p.rating().compareTo(BigDecimal.valueOf(4.0)) >= 0) {
            out.add("Aggregate rating signal is strong (simulated " + p.rating() + "/5).");
        }
        if (budgetRupee > 0) {
            BigDecimal b = BigDecimal.valueOf(budgetRupee);
            if (price.compareTo(b) <= 0) {
                out.add("Indicative street band ₹" + price.toPlainString() + " sits at or under your ₹" + budgetRupee + " budget line.");
            } else if (price.compareTo(b.multiply(BigDecimal.valueOf(1.05))) <= 0) {
                out.add("Price is only marginally above budget — discounts or card offers may close the gap.");
            }
        }
        if (out.isEmpty()) {
            out.add("Simulated catalog shows competitive specs for the declared category.");
        }
        return out.toArray(String[]::new);
    }

    private static String[] buildCons(ProductData p, int budgetRupee, BigDecimal price) {
        List<String> cons = new ArrayList<>();
        String lower = p.name().toLowerCase(Locale.ROOT);

        if (p.rating().compareTo(BigDecimal.valueOf(3.6)) < 0) {
            cons.add("Aggregate rating is middling — dig into long-tail 1★ themes (battery drift, QC, service) before buying.");
        }
        if (budgetRupee > 0) {
            BigDecimal b = BigDecimal.valueOf(budgetRupee);
            if (price.compareTo(b.multiply(BigDecimal.valueOf(1.08))) > 0) {
                cons.add("Indicative ₹" + price.toPlainString() + " exceeds your stated ₹" + budgetRupee + " budget unless you catch a sale.");
            }
        }
        if (lower.contains("poco") || lower.contains("redmi") || lower.contains("realme")) {
            cons.add("Software experience and pre-installed app policy can vary by region — read recent owner threads.");
        }
        if (lower.contains("iphone") && price.compareTo(BigDecimal.valueOf(70_000)) > 0) {
            cons.add("Premium iOS devices hold value but repair/out-of-warranty glass costs stay high.");
        }
        cons.add("This is simulated market data — validate live Flipkart/Amazon India pricing, GST invoice, and warranty.");
        cons.add("Spec sheet details (exact variant) can differ from marketing names; confirm RAM/storage/SKU.");

        return cons.stream().distinct().limit(5).toArray(String[]::new);
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static String buildSummary(ProductData p, String verdict, int budgetRupee, BigDecimal price) {
        String rupee = "₹" + price.toPlainString();
        String bPart = budgetRupee > 0 ? " against your ₹" + budgetRupee + " budget" : "";
        return switch (verdict) {
            case "BUY" -> "Signals lean positive for " + p.name() + " at an indicative " + rupee + bPart
                + " — still confirm the exact variant and seller ratings.";
            case "DONT_BUY" -> "Risk/reward looks unfavorable for " + p.name() + " at " + rupee + bPart
                + " given the simulated rating mix; look for alternatives unless pricing moves.";
            default -> "Mixed picture for " + p.name() + " near " + rupee + bPart
                + "; worth a shortlist compare with 1–2 peers before deciding.";
        };
    }

    private static String buildReasoning(ProductData p, int budgetRupee) {
        StringBuilder sb = new StringBuilder();
        sb.append("At ₹")
            .append(p.price().toPlainString())
            .append(" (indicative) with ")
            .append(p.rating())
            .append("/5 over ")
            .append(p.reviewCount())
            .append(" simulated review points, the verdict weighs value versus risk. ");
        if (p.buyerQuestion() != null && !p.buyerQuestion().isBlank()) {
            sb.append("Your question: ").append(p.buyerQuestion()).append(" ");
        }
        if (budgetRupee > 0) {
            sb.append("Budget parsed as ₹").append(budgetRupee).append(". ");
        }
        sb.append("Use Model notes and Typical features for SKU-level detail.");
        return sb.toString();
    }

    /**
     * FIXED: Fast fallback decision with no API call
     * Returns instantly instead of hanging
     */
    private LlmDecisionResult fallbackDecision(ProductData product, Throwable t) {
        log.warn("Using fallback decision for product: {}. Error: {}", product.name(), t.getMessage());
        return new LlmDecisionResult(
            "MAYBE",
            BigDecimal.valueOf(0.65),
            new String[]{
                "Product has mixed signals - check recent reviews",
                "Consider comparing with competitor alternatives",
                "Verify stock availability and warranty terms"
            },
            new String[]{
                "Market data is simulated - validate actual pricing",
                "Regional variants may differ in features and price"
            },
            "Decision is inconclusive - research more alternatives before purchase.",
            "Fallback analysis: Insufficient data for confident recommendation."
        );
    }
}