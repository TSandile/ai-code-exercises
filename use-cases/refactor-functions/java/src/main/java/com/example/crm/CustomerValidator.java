package com.example.crm;

import java.util.List;

public interface CustomerValidator {
    List<String> validate(CustomerRecord customerRecord);
}
