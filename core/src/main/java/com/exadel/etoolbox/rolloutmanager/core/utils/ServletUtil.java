/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exadel.etoolbox.rolloutmanager.core.utils;

import org.apache.commons.lang.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import java.io.IOException;
import java.util.Optional;

/**
 * Contains common methods for operating servlet request and response
 */
public class ServletUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ServletUtil.class);

    private ServletUtil() {
    }

    /**
     * Gets {@link String} parameter value from {@link SlingHttpServletRequest}
     *
     * @param request - {@link SlingHttpServletRequest}
     * @param param   - parameter name
     * @return String request parameter value
     */
    public static String getRequestParamString(SlingHttpServletRequest request, String param) {
        return Optional.ofNullable(request.getRequestParameter(param))
                .map(RequestParameter::getString)
                .orElse(StringUtils.EMPTY);
    }

    /**
     * Gets boolean parameter value from {@link SlingHttpServletRequest}
     *
     * @param request - {@link SlingHttpServletRequest}
     * @param param   - parameter name
     * @return boolean request parameter value
     */
    public static boolean getRequestParamBoolean(SlingHttpServletRequest request, String param) {
        return Boolean.parseBoolean(getRequestParamString(request, param));
    }

    /**
     * Gets int parameter value from {@link SlingHttpServletRequest}
     *
     * @param request - {@link SlingHttpServletRequest}
     * @param param   - parameter name
     * @return integer request parameter value
     */
    public static int getRequestParamInt(SlingHttpServletRequest request, String param) {
        return Optional.ofNullable(request.getRequestParameter(param))
                .map(RequestParameter::getString)
                .filter(StringUtils::isNumeric)
                .map(Integer::parseInt)
                .orElse(0);
    }

    /**
     * Gets the ID of the current user from {@link SlingHttpServletRequest}
     *
     * @param request - {@link SlingHttpServletRequest}
     * @return string value
     */
    public static String getUserId(SlingHttpServletRequest request) {
        Session session = request.getResourceResolver().adaptTo(Session.class);
        if (session == null) {
            LOG.error("Could not retrieve the current user session");
            return StringUtils.EMPTY;
        }
        return session.getUserID();
    }


    /**
     * Writes a JSON to response with UTF-8 encoding
     *
     * @param response - {@link SlingHttpServletResponse}
     * @param json - JSON string to write
     */
    public static void writeJsonResponse(SlingHttpServletResponse response, String json) {
        response.setCharacterEncoding(CharEncoding.UTF_8);
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        try {
            response.getWriter().write(json);
            response.getWriter().flush();
        } catch (IOException e) {
            LOG.error("Failed to output JSON", e);
        }
    }

    /**
     * Writes an error message to response as JSON
     *
     * @param response - {@link SlingHttpServletResponse}
     * @param status - HTTP status code
     * @param message - error message
     */
    public static void writeError(SlingHttpServletResponse response, int status, String message) {
        response.setStatus(status);
        response.addHeader("x-aem-error-pass", Boolean.TRUE.toString());
        writeJsonResponse(response, "{\"error\":\"" + message + "\"}");
    }
}