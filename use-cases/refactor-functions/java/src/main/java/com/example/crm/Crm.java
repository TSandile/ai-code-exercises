package com.example.crm;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Crm {
    private static final Logger logger = LogManager.getLogger(Crm.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CustomerRepository customerRepository = new CustomerRepository();

    public Map<String, Object> processCustomerData(List<Map<String, Object>> rawData,
            String source,
            CustomerProcessingOptions options) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> validRecords = new ArrayList<>();
        List<Map<String, Object>> invalidRecords = new ArrayList<>();
        List<Map<String, Object>> duplicateRecords = new ArrayList<>();
        Set<String> existingEmails = new HashSet<>();
        Set<String> existingPhones = new HashSet<>();
        Map<String, Integer> errorCounts = new HashMap<>();
        int processedCount = 0;
        int successCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        if (rawData == null || rawData.isEmpty()) {
            result.put("status", "error");
            result.put("message", "No data provided for processing");
            return result;
        }

        logger.info("Starting to process {} customer records from source: {}",
                rawData.size(), source);

        if (options.isPerformDeduplication()) {
            if (!loadExistingCustomers(existingEmails, existingPhones, result)) {
                return result;
            }
        }

        for (Map<String, Object> rawRecord : rawData) {
            processedCount++;

            if (options.getMaxErrorCount() > 0 && errorCount >= options.getMaxErrorCount()) {
                logger.warn("Maximum error threshold reached ({}). Skipping remaining records.",
                        options.getMaxErrorCount());
                skippedCount = rawData.size() - processedCount + 1;
                break;
            }

            List<String> recordErrors = new ArrayList<>();
            CustomerRecord record = buildRecord(rawRecord, source, recordErrors);

            if (options.getCustomValidator() != null) {
                recordErrors.addAll(options.getCustomValidator().validate(record));
            }

            DuplicateCheck duplicateCheck = checkForDuplicates(record,
                    existingEmails, existingPhones, validRecords);

            if (duplicateCheck.isDuplicate()) {
                if (options.isDuplicatesAreErrors()) {
                    recordErrors.addAll(duplicateCheck.getErrors());
                } else {
                    duplicateRecords.add(record.toMap());
                    skippedCount++;
                    continue;
                }
            }

            if (recordErrors.isEmpty()) {
                validRecords.add(record.toMap());
                successCount++;
                addToDedupSets(record, existingEmails, existingPhones);
            } else {
                Map<String, Object> invalidMap = record.toMap();
                invalidMap.put("errors", recordErrors);
                invalidRecords.add(invalidMap);
                errorCount++;
                updateErrorCounts(errorCounts, recordErrors);
            }
        }

        if (options.isSaveToDatabase() && !validRecords.isEmpty()) {
            if (!saveValidCustomers(source, validRecords, result)) {
                return result;
            }
        }

        long processingTimeMs = System.currentTimeMillis() - startTime;
        buildResult(result, source, rawData.size(), processedCount, successCount,
                errorCount, skippedCount, duplicateRecords.size(), processingTimeMs,
                errorCounts, options, validRecords, invalidRecords, duplicateRecords);

        logger.info("Customer data processing completed. Total: {}, Success: {}, Error: {}, Skipped: {}, Time: {} ms",
                rawData.size(), successCount, errorCount, skippedCount, processingTimeMs);

        return result;
    }

    private boolean loadExistingCustomers(Set<String> existingEmails,
            Set<String> existingPhones,
            Map<String, Object> result) {
        try {
            List<Customer> existingCustomers = customerRepository.findAll();
            for (Customer customer : existingCustomers) {
                if (customer.getEmail() != null && !customer.getEmail().isEmpty()) {
                    existingEmails.add(customer.getEmail().toLowerCase());
                }
                if (customer.getPhoneNumber() != null && !customer.getPhoneNumber().isEmpty()) {
                    existingPhones.add(normalizePhoneNumber(customer.getPhoneNumber()));
                }
            }
            logger.info("Loaded {} existing customers for deduplication",
                    existingCustomers.size());
            return true;
        } catch (Exception e) {
            logger.error("Error loading existing customers for deduplication: {}",
                    e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "Failed to load existing customers for deduplication");
            result.put("error", e.getMessage());
            return false;
        }
    }

    private CustomerRecord buildRecord(Map<String, Object> rawRecord,
            String source,
            List<String> recordErrors) {
        Map<String, Object> normalizedRecord = preprocessSource(rawRecord, source);

        String email = normalizeText(getString(normalizedRecord.get("email"))).toLowerCase();
        if (email.isEmpty()) {
            recordErrors.add("Missing required field: email");
        } else if (!isValidEmail(email)) {
            recordErrors.add("Invalid email format: " + email);
        }

        String firstName = normalizeName(getString(normalizedRecord.get("firstName")));
        String lastName = normalizeName(getString(normalizedRecord.get("lastName")));
        if (firstName.isEmpty()) {
            recordErrors.add("Missing required field: firstName");
        }
        if (lastName.isEmpty()) {
            recordErrors.add("Missing required field: lastName");
        }

        String rawPhone = getString(normalizedRecord.get("phone"));
        String phone = "";
        if (!rawPhone.isEmpty()) {
            String normalizedPhone = normalizePhoneNumber(rawPhone);
            if (!isValidPhoneNumber(normalizedPhone)) {
                recordErrors.add("Invalid phone number format: " + rawPhone);
            } else {
                phone = formatPhoneNumber(normalizedPhone);
            }
        }

        CustomerRecord.Address address = null;
        if (normalizedRecord.containsKey("address") && normalizedRecord.get("address") != null) {
            Object addressField = normalizedRecord.get("address");
            address = parseAddress(addressField, recordErrors);
        }

        LocalDate dateOfBirth = null;
        if (normalizedRecord.containsKey("dateOfBirth") &&
                normalizedRecord.get("dateOfBirth") != null &&
                !getString(normalizedRecord.get("dateOfBirth")).isEmpty()) {
            String dobString = getString(normalizedRecord.get("dateOfBirth"));
            try {
                dateOfBirth = LocalDate.parse(dobString, DATE_FORMATTER);
                if (!isValidAge(dateOfBirth)) {
                    recordErrors.add("Invalid date of birth (age must be between 18 and 120): " + dobString);
                }
            } catch (DateTimeParseException e) {
                recordErrors.add("Invalid date format for date of birth: " + dobString);
            }
        }

        CustomerRecord record = new CustomerRecord();
        record.setEmail(email.isEmpty() ? null : email);
        record.setFirstName(firstName.isEmpty() ? null : firstName);
        record.setLastName(lastName.isEmpty() ? null : lastName);
        record.setPhone(phone.isEmpty() ? null : phone);
        record.setAddress(address);
        record.setDateOfBirth(dateOfBirth);
        return record;
    }

    private Map<String, Object> preprocessSource(Map<String, Object> record,
            String source) {
        if ("csv".equalsIgnoreCase(source)) {
            return preprocessCsvRecord(record);
        }
        if ("api".equalsIgnoreCase(source)) {
            return preprocessApiRecord(record);
        }
        if ("manual".equalsIgnoreCase(source)) {
            return preprocessManualRecord(record);
        }
        return record;
    }

    private DuplicateCheck checkForDuplicates(CustomerRecord record,
            Set<String> existingEmails,
            Set<String> existingPhones,
            List<Map<String, Object>> validRecords) {
        List<String> duplicateErrors = new ArrayList<>();

        if (record.getEmail() != null) {
            String normalizedEmail = record.getEmail().toLowerCase();
            if (existingEmails.contains(normalizedEmail) || batchContainsEmail(normalizedEmail, validRecords)) {
                duplicateErrors.add("Duplicate email: " + record.getEmail());
                return new DuplicateCheck(true, duplicateErrors);
            }
        }

        if (record.getPhone() != null) {
            String normalizedPhone = normalizePhoneNumber(record.getPhone());
            if (existingPhones.contains(normalizedPhone) || batchContainsPhone(normalizedPhone, validRecords)) {
                duplicateErrors.add("Duplicate phone number: " + record.getPhone());
                return new DuplicateCheck(true, duplicateErrors);
            }
        }

        return new DuplicateCheck(false, Collections.emptyList());
    }

    private boolean batchContainsEmail(String email,
            List<Map<String, Object>> validRecords) {
        for (Map<String, Object> record : validRecords) {
            if (record.containsKey("email") && email.equalsIgnoreCase(getString(record.get("email")))) {
                return true;
            }
        }
        return false;
    }

    private boolean batchContainsPhone(String phone,
            List<Map<String, Object>> validRecords) {
        for (Map<String, Object> record : validRecords) {
            if (record.containsKey("phone") && phone.equals(normalizePhoneNumber(getString(record.get("phone"))))) {
                return true;
            }
        }
        return false;
    }

    private void addToDedupSets(CustomerRecord record,
            Set<String> existingEmails,
            Set<String> existingPhones) {
        if (record.getEmail() != null) {
            existingEmails.add(record.getEmail().toLowerCase());
        }
        if (record.getPhone() != null) {
            existingPhones.add(normalizePhoneNumber(record.getPhone()));
        }
    }

    private boolean saveValidCustomers(String source,
            List<Map<String, Object>> validRecords,
            Map<String, Object> result) {
        try {
            List<Customer> customers = new ArrayList<>();
            for (Map<String, Object> record : validRecords) {
                Customer customer = mapToCustomerEntity(record, source);
                customers.add(customer);
            }
            customerRepository.saveAll(customers);
            logger.info("Successfully saved {} customer records to database", customers.size());
            return true;
        } catch (Exception e) {
            logger.error("Error saving records to database: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "Failed to save valid records to database");
            result.put("error", e.getMessage());
            return false;
        }
    }

    private void buildResult(Map<String, Object> result,
            String source,
            int totalRecords,
            int processedCount,
            int successCount,
            int errorCount,
            int skippedCount,
            int duplicateCount,
            long processingTimeMs,
            Map<String, Integer> errorCounts,
            CustomerProcessingOptions options,
            List<Map<String, Object>> validRecords,
            List<Map<String, Object>> invalidRecords,
            List<Map<String, Object>> duplicateRecords) {
        result.put("status", "success");
        result.put("source", source);
        result.put("totalRecords", totalRecords);
        result.put("processedCount", processedCount);
        result.put("successCount", successCount);
        result.put("errorCount", errorCount);
        result.put("skippedCount", skippedCount);
        result.put("duplicateCount", duplicateCount);
        result.put("processingTimeMs", processingTimeMs);
        result.put("errorsByType", errorCounts);

        if (options.isIncludeRecordsInResponse()) {
            if (options.isIncludeValidRecords()) {
                result.put("validRecords", validRecords);
            }
            if (options.isIncludeInvalidRecords()) {
                result.put("invalidRecords", invalidRecords);
            }
            if (options.isIncludeDuplicateRecords()) {
                result.put("duplicateRecords", duplicateRecords);
            }
        }
    }

    private CustomerRecord.Address parseAddress(Object addressField,
            List<String> recordErrors) {
        Map<String, Object> addressData;
        if (addressField instanceof Map) {
            addressData = (Map<String, Object>) addressField;
        } else if (addressField instanceof String) {
            addressData = parseAddressString(addressField.toString());
        } else {
            recordErrors.add("Invalid address format");
            return null;
        }

        String street = normalizeText(getString(addressData.get("street")));
        String city = capitalizeWords(normalizeText(getString(addressData.get("city"))));
        String country = normalizeText(getString(addressData.get("country")));
        String state = normalizeText(getString(addressData.get("state"))).toUpperCase();
        String zip = normalizeText(getString(addressData.get("zip")));

        if (!country.isEmpty() && country.length() <= 3) {
            String fullCountryName = getCountryNameFromCode(country);
            if (fullCountryName == null) {
                recordErrors.add("Invalid country code: " + country);
            } else {
                country = fullCountryName;
            }
        }

        if (!state.isEmpty() && ("US".equalsIgnoreCase(country) || "CA".equalsIgnoreCase(country))) {
            if (!isValidStateOrProvince(state, country)) {
                recordErrors.add("Invalid state/province: " + state);
            }
        }

        if (!zip.isEmpty() && !country.isEmpty() && !isValidPostalCode(zip, country)) {
            recordErrors.add("Invalid postal code format for " + country + ": " + zip);
        }

        CustomerRecord.Address address = new CustomerRecord.Address();
        address.setStreet(street.isEmpty() ? null : street);
        address.setCity(city.isEmpty() ? null : city);
        address.setState(state.isEmpty() ? null : state);
        address.setZip(zip.isEmpty() ? null : zip);
        address.setCountry(country.isEmpty() ? null : country);
        return address;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String getString(Object value) {
        return value == null ? "" : value.toString();
    }

    private String normalizeName(String text) {
        text = normalizeText(text);
        if (text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    private boolean isValidAge(LocalDate dateOfBirth) {
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        return age >= 18 && age <= 120;
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private String normalizePhoneNumber(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9+]", "");
    }

    private boolean isValidPhoneNumber(String phone) {
        return PHONE_PATTERN.matcher(phone).matches();
    }

    private String formatPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        if (phone.startsWith("+")) {
            return phone;
        }
        if (phone.length() == 10) {
            return "+1" + phone;
        }
        return phone;
    }

    private Map<String, Object> preprocessCsvRecord(Map<String, Object> record) {
        return record;
    }

    private Map<String, Object> preprocessApiRecord(Map<String, Object> record) {
        return record;
    }

    private Map<String, Object> preprocessManualRecord(Map<String, Object> record) {
        return record;
    }

    private Map<String, Object> parseAddressString(String addressString) {
        Map<String, Object> addressComponents = new HashMap<>();
        String[] pieces = addressString.split(",");
        if (pieces.length >= 1) {
            addressComponents.put("street", pieces[0].trim());
        }
        if (pieces.length >= 2) {
            addressComponents.put("city", pieces[1].trim());
        }
        if (pieces.length >= 3) {
            addressComponents.put("state", pieces[2].trim());
        }
        if (pieces.length >= 4) {
            addressComponents.put("zip", pieces[3].trim());
        }
        if (pieces.length >= 5) {
            addressComponents.put("country", pieces[4].trim());
        }
        return addressComponents;
    }

    private String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    private boolean isValidStateOrProvince(String code, String country) {
        if (country == null) {
            return false;
        }
        String normalizedCountry = country.toUpperCase();
        if ("US".equals(normalizedCountry)) {
            return US_STATES.contains(code);
        }
        if ("CA".equals(normalizedCountry)) {
            return CANADA_PROVINCES.contains(code);
        }
        return true;
    }

    private boolean isValidPostalCode(String postalCode, String country) {
        if (postalCode == null || postalCode.isEmpty() || country == null) {
            return true;
        }
        String normalizedCountry = country.toUpperCase();
        if ("US".equals(normalizedCountry)) {
            return postalCode.matches("^\\d{5}(-\\d{4})?$");
        }
        if ("CA".equals(normalizedCountry)) {
            return postalCode.matches("^[A-Za-z]\\d[A-Za-z] ?\\d[A-Za-z]\\d$");
        }
        return true;
    }

    private String getCountryNameFromCode(String countryCode) {
        if (countryCode == null) {
            return null;
        }
        switch (countryCode.toUpperCase()) {
            case "US":
                return "United States";
            case "CA":
                return "Canada";
            case "UK":
            case "GB":
                return "United Kingdom";
            default:
                return null;
        }
    }

    private void updateErrorCounts(Map<String, Integer> errorCounts,
            List<String> recordErrors) {
        for (String error : recordErrors) {
            String errorType = error.split(":")[0].trim();
            errorCounts.put(errorType,
                    errorCounts.getOrDefault(errorType, 0) + 1);
        }
    }

    private Customer mapToCustomerEntity(Map<String, Object> record, String source) {
        String firstName = getString(record.get("firstName"));
        String lastName = getString(record.get("lastName"));
        String email = getString(record.get("email"));
        String phone = getString(record.get("phone"));
        String fullName = buildFullName(firstName, lastName);

        Customer customer = new Customer(fullName, email, phone);
        customer.setDataSource(source);
        customer.setCreatedAt(Instant.now());
        return customer;
    }

    private String buildFullName(String firstName, String lastName) {
        if (firstName.isEmpty()) {
            return lastName;
        }
        if (lastName.isEmpty()) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    private static final Set<String> US_STATES = new HashSet<>();
    private static final Set<String> CANADA_PROVINCES = new HashSet<>();

    static {
        Collections.addAll(US_STATES,
                "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE",
                "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS",
                "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS",
                "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY",
                "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
                "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV",
                "WI", "WY");
        Collections.addAll(CANADA_PROVINCES,
                "AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU",
                "ON", "PE", "QC", "SK", "YT");
    }

    private static class DuplicateCheck {
        private final boolean duplicate;
        private final List<String> errors;

        private DuplicateCheck(boolean duplicate, List<String> errors) {
            this.duplicate = duplicate;
            this.errors = errors;
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
