# Restricted double compare singe swap

The spec:

    RDCSS(a1: ARef[Object], o1: Object,
          a2: ARef[Object], o2: Object,
	  n2: Object): Object = {
      atomic {
        Object r = a2.get
	if ((r eq o2) && (a1.get eq o1)) a2.set(n2)
	r
      }
    }

A descriptor:

    case class RDCSSDescriptor(
      a1: ARef[Object], o1: Object,
      a2: ARef[Object + RDCSSDescriptor], o2: Object,
      n2: Object
    );

The implementation:

    RDCSS(d: RDCSSDescriptor): Object = {    
      val read = d.a2.get
      read match {
        case Inl(cur) if cur eq d.o2 => 
	  val wd = Inr(d)
	  if (d.a2 CAS (read, wd)) { Complete(wd); cur }
	  else RDCSS(d)
	case Inl(cur) => cur
	case Inr(d') => { Complete(read); RDCSS(d) }
      }
    }
    
    Complete(RDCSSDescriptor d) {
      if (d.a1.get eq d.o1)
        d.a2 CAS (
      else 
    }
