package com.invi.api.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.invi.api.invitation.Invitation;
import com.invi.api.invitation.InvitationRepository;
import com.invi.api.template.Template;
import com.invi.api.template.TemplateRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Collects references without interpreting, filtering, or changing saved page content. */
@Service
public class MediaReferences {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String UPLOAD_UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private static final Pattern MANAGED_KEY = Pattern.compile(
            "invitations/" + UUID_PATTERN + "/" + UPLOAD_UUID_PATTERN + "\\.(?:jpg|png|webp)");
    private static final int MAX_PERCENT_DECODING_PASSES = 2;

    private final InvitationRepository invitationRepository;
    private final TemplateRepository templateRepository;

    public MediaReferences(InvitationRepository invitationRepository, TemplateRepository templateRepository) {
        this.invitationRepository = invitationRepository;
        this.templateRepository = templateRepository;
    }

    /** Repository/scan failures deliberately propagate: a partial reference set is unsafe for deletion. */
    @Transactional(readOnly = true)
    public Set<String> allKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Invitation invitation : invitationRepository.findAll()) {
            keys.addAll(extract(invitation.getPageData()));
            keys.addAll(extract(invitation.getLangVariants()));
        }
        for (Template template : templateRepository.findAll()) {
            keys.addAll(extract(template.getDefaultPageData()));
            keys.addAll(extractText(template.getThumbnailUrl()));
        }
        return keys;
    }

    /** Iterative traversal has no depth/node cutoff and includes unknown fields and object field names. */
    public static Set<String> extract(JsonNode root) {
        Set<String> keys = new LinkedHashSet<>();
        if (root == null) {
            return keys;
        }
        Deque<JsonNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            JsonNode node = pending.pop();
            if (node.isTextual()) {
                keys.addAll(extractText(node.textValue()));
            } else if (node.isObject()) {
                var fields = node.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    keys.addAll(extractText(field.getKey()));
                    pending.push(field.getValue());
                }
            } else if (node.isArray()) {
                for (JsonNode child : node) {
                    pending.push(child);
                }
            }
        }
        return keys;
    }

    /**
     * Search arbitrary text, not just URLs. Hosts, CDN prefixes, queries, fragments, HTML and CSS
     * are intentionally unrestricted. Substring matches may over-protect, never authorize deletion.
     */
    public static Set<String> extractText(String text) {
        Set<String> keys = new LinkedHashSet<>();
        if (text == null) {
            return keys;
        }
        String candidate = text;
        for (int pass = 0; pass <= MAX_PERCENT_DECODING_PASSES; pass++) {
            addMatches(keys, candidate);
            addBrowserAliases(keys, candidate, MAX_PERCENT_DECODING_PASSES - pass);
            if (pass == MAX_PERCENT_DECODING_PASSES) {
                break;
            }
            String decoded = decodePercentOctets(candidate);
            if (decoded.equals(candidate)) {
                break;
            }
            candidate = decoded;
        }
        return keys;
    }

    private static void addMatches(Set<String> keys, String text) {
        Matcher matcher = MANAGED_KEY.matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group());
        }
    }

    /**
     * Add WHATWG-style aliases without replacing any raw-text protection. HTTP(S) URLs accept
     * backslashes as slashes and discard ASCII TAB/CR/LF. Apply that conservatively to all text,
     * then resolve dot segments in path-like tokens, including URLs embedded in HTML/CSS.
     * Query/fragment and markup boundaries cannot consume a preceding path during resolution.
     */
    private static void addBrowserAliases(Set<String> keys, String text, int remainingDecodingPasses) {
        String browserText = browserText(text);
        addMatches(keys, browserText);
        addWholeTextPathAliases(keys, browserText);
        int start = 0;
        for (int end = 0; end <= browserText.length(); end++) {
            if (end == browserText.length() || isPathBoundary(browserText.charAt(end))) {
                if (start < end) {
                    addNormalizedToken(keys, browserText.substring(start, end), remainingDecodingPasses);
                }
                start = end + 1;
            }
        }
    }

    /**
     * Do not treat punctuation/space inside an actual URL path as markup boundaries.
     * Observe each resolved three-segment suffix BEFORE later prose/query segments can
     * pop it. A managed key has exactly three segments. This only adds protection and
     * intentionally accepts false positives; the strict inventory eligibility stays unchanged.
     */
    private static void addWholeTextPathAliases(Set<String> keys, String text) {
        java.util.List<String> segments = new java.util.ArrayList<>();
        for (String segment : text.split("/", -1)) {
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) segments.removeLast();
            } else if (!".".equals(segment)) {
                segments.add(segment);
                int from = Math.max(0, segments.size() - 3);
                addMatches(keys, String.join("/", segments.subList(from, segments.size())));
            }
        }
    }

    /** Also decode after tokenization so an encoded space/quote in a removed segment cannot hide a key. */
    private static void addNormalizedToken(Set<String> keys, String token, int remainingDecodingPasses) {
        String candidate = token;
        for (int pass = 0; pass <= remainingDecodingPasses; pass++) {
            String browserToken = browserText(candidate);
            addMatches(keys, browserToken);
            addMatches(keys, resolveDotSegments(browserToken));
            if (pass == remainingDecodingPasses) {
                break;
            }
            String decoded = decodePercentOctets(candidate);
            if (decoded.equals(candidate)) {
                break;
            }
            candidate = decoded;
        }
    }

    private static String browserText(String text) {
        return text.replace('\\', '/').replace("\t", "").replace("\r", "").replace("\n", "");
    }

    private static boolean isPathBoundary(char value) {
        return Character.isWhitespace(value) || switch (value) {
            case '"', '\'', '<', '>', '(', ')', '[', ']', '{', '}', '?', '#' -> true;
            default -> false;
        };
    }

    /** Keep empty path segments: in /a//../b, '..' removes the empty segment, not 'a'. */
    private static String resolveDotSegments(String path) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/", -1)) {
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
            } else if (!".".equals(segment)) {
                segments.addLast(segment);
            }
        }
        return String.join("/", segments);
    }

    /** Only exact, lowercase keys generated by our upload namespace are eligible for cleanup. */
    public static boolean isManagedKey(String key) {
        return key != null && MANAGED_KEY.matcher(key).matches();
    }

    /**
     * Keys use ASCII only, so decoding valid octets is sufficient without URL/form normalization.
     * Keep '+' and malformed percent sequences intact, and still decode valid sequences nearby.
     */
    private static String decodePercentOctets(String text) {
        StringBuilder decoded = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '%' && i + 2 < text.length()) {
                int high = hexValue(text.charAt(i + 1));
                int low = hexValue(text.charAt(i + 2));
                if (high >= 0 && low >= 0) {
                    decoded.append((char) ((high << 4) | low));
                    i += 2;
                    continue;
                }
            }
            decoded.append(current);
        }
        return decoded.toString();
    }

    private static int hexValue(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return -1;
    }
}
