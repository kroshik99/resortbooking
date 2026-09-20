package com.resortapi.resortbooking.exception;

public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(String roomTypeName, int capacity, int requested) {
        super("A " + roomTypeName + " room sleeps " + capacity + ", but " + requested + " guests were requested");
    }
}
