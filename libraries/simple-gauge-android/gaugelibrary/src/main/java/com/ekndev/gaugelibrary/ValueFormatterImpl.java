package com.ekndev.gaugelibrary;

import com.ekndev.gaugelibrary.contract.ValueFormatter;

public class ValueFormatterImpl implements ValueFormatter {
    @Override
    public String getFormattedValue(double value) {
        return String.valueOf(value);
    }
}
