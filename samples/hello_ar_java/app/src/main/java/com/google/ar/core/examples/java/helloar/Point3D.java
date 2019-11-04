package com.google.ar.core.examples.java.helloar;

class Point3D {

    public float x;
    public float y;
    public float z;

    public Point3D(float x,float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public double distanceTo(Point3D p) {
        return Math.sqrt(Math.pow(this.x - p.getX(), 2) + Math.pow(this.y - p.getY(), 2) + Math.pow(this.z - p.getZ(), 2));
    }
}
