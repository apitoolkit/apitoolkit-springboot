package io.apitoolkit.springboot;

import java.text.SimpleDateFormat;
import java.util.*;

import jakarta.servlet.http.HttpServletRequest;

public class APErrors {
  private static Throwable rootCause(Throwable err) {
    Throwable cause = err;
    while (cause != null && cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }

  private static boolean isError(Object value) {
    return value instanceof Throwable;
  }

  public static Map<String, Object> buildError(Throwable err) {
    String errType = err.getClass().getName();
    Throwable rootError = rootCause(err);
    String rootErrorType = rootError.getClass().getName();
    Date currentDate = new Date();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    String isoString = dateFormat.format(currentDate);

    Map<String, Object> errorInfo = new HashMap<>();
    errorInfo.put("when", isoString);
    errorInfo.put("error_type", errType);
    errorInfo.put("message", err.getMessage());
    errorInfo.put("root_error_type", rootErrorType);
    errorInfo.put("root_error_message", rootError.getMessage());
    errorInfo.put("stack_trace", getStackTraceAsString(err));
    return errorInfo;
  }

  private static String getStackTraceAsString(Throwable throwable) {
    StringBuilder sb = new StringBuilder();
    for (StackTraceElement element : throwable.getStackTrace()) {
      sb.append(element.toString()).append("\n");
    }
    return sb.toString();
  }

  public static void reportError(HttpServletRequest request, Throwable e) {
    if (isError(e)) {
      Map<String, Object> error = buildError(e);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> errorList = (List<Map<String, Object>>) request.getAttribute("APITOOLKIT_ERRORS");
      if (errorList == null) {
        errorList = new ArrayList<>();
      }
      errorList.add(error);
      request.setAttribute("APITOOLKIT_ERRORS", errorList);
    }
  }

}
