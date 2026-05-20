package com.example.crm;

import java.util.Collections;
import java.util.List;

public class DefaultValidator implements CustomerValidator {
    @Override
    public List<String> validate(CustomerRecord customerRecord) {
        return Collections.emptyList();
    }
}
