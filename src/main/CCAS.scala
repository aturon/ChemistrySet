package chemistry

import java.util.concurrent.atomic._

// A record describing an in-progress CCAS operation
private case class Descriptor[A]()

// Conditional compare-and-set, due to Harris, Fraser and Pratt
class CCASRef[A] {
  private object value;
}
