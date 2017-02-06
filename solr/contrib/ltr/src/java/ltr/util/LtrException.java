package ltr.util;

public class LtrException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LtrException(String message) {
    super(message);
  }

  public LtrException(String message, Exception parent) {
    super(message, parent);
  }

}
