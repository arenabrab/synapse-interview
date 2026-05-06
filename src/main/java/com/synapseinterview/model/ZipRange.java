package com.synapseinterview.model;

public record ZipRange(int min, int max) {
    public boolean contains(int zip) {
        return zip >= min && zip <= max;
    }
}
