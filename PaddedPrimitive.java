

class PaddedPrimitive<T> {
  volatile long pad1;
  volatile long pad2;
  volatile long pad3;
  volatile long pad4;
  volatile long pad5;
  volatile long pad6;
  volatile long pad7;
  volatile long pad8;
  volatile T value;
  volatile long pad11;
  volatile long pad12;
  volatile long pad13;
  volatile long pad14;
  volatile long pad15;
  volatile long pad16;
  volatile long pad17;
  volatile long pad18;
  
  public PaddedPrimitive(T value) {
    this.value = value;
  }
}

class PaddedPrimitiveNonVolatile<T> {
  long pad1;
  long pad2;
  long pad3;
  long pad4;
  long pad5;
  long pad6;
  long pad7;
  long pad8;
  T value;
  long pad11;
  long pad12;
  long pad13;
  long pad14;
  long pad15;
  long pad16;
  long pad17;
  long pad18;
  
  public PaddedPrimitiveNonVolatile(T value) {
    this.value = value;
  }
}

