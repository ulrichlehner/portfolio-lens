package io.portfoliolens.page;

/** The one escaping function of the page: every value written into HTML or SVG goes through it. */
public final class Html
{
    private Html()
    {
    }

    /** Escapes text for element content and quoted attribute values. */
    public static String escape(String text)
    {
        if (text == null)
            return "";
        var out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++)
        {
            var c = text.charAt(i);
            switch (c)
            {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> {
                    // control characters other than tab and line breaks are not valid in XML
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r')
                        out.append(' ');
                    else
                        out.append(c);
                }
            }
        }
        return out.toString();
    }

    /** JSON for a {@code <script type="application/json">} block: no {@code </script>} can appear. */
    public static String scriptJson(String json)
    {
        return json.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }
}
