package org.dariusturcu.backend.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

public final class RequestPaths {

    private static final String PATH_SEPARATOR = "/";

    private RequestPaths() {
    }

    /**
     * The request path below the context path, percent-decoded, stripped of path
     * parameters and duplicate separators, and without a trailing separator. Matching on
     * this form means an encoded spelling of a path reaches the same rules as the plain one.
     */
    public static String normalizedPath(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        while (path.length() > 1 && path.endsWith(PATH_SEPARATOR)) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
