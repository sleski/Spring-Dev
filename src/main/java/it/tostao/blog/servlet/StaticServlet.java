package it.tostao.blog.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * @author Slawomir Leski <s.leski@e-media.de>
 *
 */

public class StaticServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(StaticServlet.class);

    private static final long serialVersionUID = 19187819302996632L;

    /**
     * look up result interface
     *
     */
    public interface LookupResult {

        /**
         * respond get
         * @param resp
         * @throws IOException
         */
        void respondGet(HttpServletResponse resp) throws IOException;

        /**
         * respong head
         * @param resp
         */
        void respondHead(HttpServletResponse resp);

        /**
         * last modified
         * @return
         */
        long getLastModified();
    }

    /**
     * error
     */
    public static class Error implements LookupResult {

        private final int statusCode;
        private final String message;

        /**
         * ctor
         * @param statusCode
         * @param message
         */
        public Error(int statusCode, String message) {
            this.statusCode = statusCode;
            this.message = message;
        }

        @Override
        public long getLastModified() {
            return -1;
        }

        @Override
        public void respondGet(HttpServletResponse resp) throws IOException {
            resp.sendError(statusCode, message);
        }

        @Override
        public void respondHead(HttpServletResponse resp) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * static file
     */
    public static class StaticFile implements LookupResult {

        private final long lastModified;
        private final String mimeType;
        private final int contentLength;
        private final boolean acceptsDeflate;
        private final URL url;

        /**
         * ctor
         * @param lastModified
         * @param mimeType
         * @param contentLength
         * @param acceptsDeflate
         * @param url
         */
        public StaticFile(long lastModified, String mimeType, int contentLength, boolean acceptsDeflate, URL url) {
            this.lastModified = lastModified;
            this.mimeType = mimeType;
            this.contentLength = contentLength;
            this.acceptsDeflate = acceptsDeflate;
            this.url = url;
        }

        @Override
        public long getLastModified() {
            return lastModified;
        }

        /**
         * will deflate
         * @return
         */
        protected boolean willDeflate() {
            return acceptsDeflate && deflatable(mimeType) && contentLength >= DEFLATE_THRESHOLD;
        }

        /**
         * headers
         * @param resp
         */
        protected void setHeaders(HttpServletResponse resp) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType(mimeType);
            if (contentLength >= 0 && !willDeflate()) {
                resp.setContentLength(contentLength);
            }
            resp.setHeader("Cache-Control", "public,max-age=31556926");
        }

        @Override
        public void respondGet(HttpServletResponse resp) throws IOException {
            setHeaders(resp);
            final OutputStream os;
            if (willDeflate()) {
                resp.setHeader("Content-Encoding", "gzip");
                os = new GZIPOutputStream(resp.getOutputStream(), BUFFERSIZE);
            } else {
                os = resp.getOutputStream();
            }
            InputStream openStream = null;
            try {
                openStream = url.openStream();
                IOUtils.copy(openStream, os);
            } finally {
                os.close();
                if (openStream != null) {
                    openStream.close();
                }
            }

        }

        @Override
        public void respondHead(HttpServletResponse resp) {
            if (willDeflate()) {
                throw new UnsupportedOperationException();
            }
            setHeaders(resp);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        lookup(req).respondGet(resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "put not supported");
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            lookup(req).respondHead(resp);
        } catch (UnsupportedOperationException e) {
            super.doHead(req, resp);
        }
    }

    @Override
    protected long getLastModified(HttpServletRequest req) {
        return lookup(req).getLastModified();
    }

    /**
     * lookup
     * @param req
     * @return
     */
    protected LookupResult lookup(HttpServletRequest req) {
        LookupResult r = (LookupResult) req.getAttribute("lookupResult");
        if (r == null) {
            r = lookupNoCache(req);
            req.setAttribute("lookupResult", r);
        }
        return r;
    }

    private static final Pattern SALTED_STATIC_PATTERN = Pattern.compile("^\\/static\\/s\\/[0-9a-zA-Z]{4,40}(\\/.*)");

    /**
     * lookup cache
     * @param req
     * @return
     */
    protected LookupResult lookupNoCache(HttpServletRequest req) {
        String path = getPath(req);
        Matcher matcher = SALTED_STATIC_PATTERN.matcher(path);
        if (matcher.find()) {
            path = "/static" + matcher.group(1);
        }

        if (isForbidden(path)) {
            return new Error(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        }

        final URL url;
        try {
            url = getServletContext().getResource(path);
        } catch (MalformedURLException e) {
            return new Error(HttpServletResponse.SC_BAD_REQUEST, "Malformed path");
        }
        if (url == null) {
            LOG.warn("resource not found: " + path);
            return new Error(HttpServletResponse.SC_NOT_FOUND, "Not found");
        }

        final String mimeType = getMimeType(path);

        final String realpath = getServletContext().getRealPath(path);
        if (realpath != null) {
            // Try as an ordinary file
            File f = new File(realpath);
            if (!f.isFile()) {
                return new Error(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            } else {
                return new StaticFile(f.lastModified(), mimeType, (int) f.length(), acceptsDeflate(req), url);
            }
        } else {
            try {
                // Try as a JAR Entry
                final ZipEntry ze = ((JarURLConnection) url.openConnection()).getJarEntry();
                if (ze != null) {
                    if (ze.isDirectory()) {
                        return new Error(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                    } else {
                        return new StaticFile(ze.getTime(), mimeType, (int) ze.getSize(), acceptsDeflate(req), url);
                    }
                } else {
                    // Unexpected?
                    return new StaticFile(-1, mimeType, -1, acceptsDeflate(req), url);
                }
            } catch (ClassCastException e) {
                // Unknown resource type
                return new StaticFile(-1, mimeType, -1, acceptsDeflate(req), url);
            } catch (IOException e) {
                return new Error(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
            }
        }
    }

    /**
     * get path
     * @param req
     * @return
     */
    protected String getPath(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        String pathInfo = coalesce(req.getPathInfo(), "");
        return servletPath + pathInfo;
    }

    /**
     * is forbidden
     * @param path
     * @return
     */
    protected boolean isForbidden(String path) {
        String lpath = path.toLowerCase();
        return lpath.startsWith("/web-inf/") || lpath.startsWith("/meta-inf/");
    }

    /**
     * mime type
     * @param path
     * @return
     */
    protected String getMimeType(String path) {
        String mimeType = getServletContext().getMimeType(path);
        if (mimeType == null) {
            return "application/octet-stream";
        }
        if (mimeType.endsWith("javascript") || mimeType.endsWith("css")) {
            return mimeType + "; charset=utf-8";
        }
        return coalesce(getServletContext().getMimeType(path), "application/octet-stream");
    }

    /**
     *
     * @param req
     * @return
     */
    protected static boolean acceptsDeflate(HttpServletRequest req) {
        final String ae = req.getHeader("Accept-Encoding");
        return ae != null && ae.contains("gzip");
    }

    /**
     * deflate table
     * @param mimetype
     * @return
     */
    protected static boolean deflatable(String mimetype) {
        return mimetype.startsWith("text/") || mimetype.equals("application/postscript")
                || mimetype.startsWith("application/ms") || mimetype.startsWith("application/vnd")
                || mimetype.endsWith("xml");
    }

    private static final int DEFLATE_THRESHOLD = 4 * 1024;

    private static final int BUFFERSIZE = 4 * 1024;

    /**
     * keine ahnung
     * @param ts
     * @return
     */
    public static <T> T coalesce(T... ts) {
        for (T t : ts) {
            if (t != null) {
                return t;
            }
        }
        return null;
    }
}
