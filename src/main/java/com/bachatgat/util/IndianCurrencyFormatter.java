package com.bachatgat.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class IndianCurrencyFormatter {

    private IndianCurrencyFormatter() {}

    /**
     * Formats a BigDecimal amount in Indian Rupee format, e.g. ₹1,00,000.00 or ₹5,000.00
     */
    public static String formatINR(BigDecimal amount) {
        if (amount == null) {
            return "₹0.00";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("en", "IN"));
        symbols.setCurrencySymbol("₹");
        DecimalFormat format = new DecimalFormat("₹##,##,##0.00", symbols);
        return format.format(amount);
    }

    /**
     * Formats an integer amount without decimals: e.g. ₹1,00,000
     */
    public static String formatINRAmount(BigDecimal amount) {
        if (amount == null) {
            return "₹0";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("en", "IN"));
        symbols.setCurrencySymbol("₹");
        DecimalFormat format = new DecimalFormat("₹##,##,##0", symbols);
        return format.format(amount);
    }
}
