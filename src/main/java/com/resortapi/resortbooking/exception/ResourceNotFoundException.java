package com.resortapi.resortbooking.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String what, Object id) {
        super(what + " " + id + " was not found");
    }
}
