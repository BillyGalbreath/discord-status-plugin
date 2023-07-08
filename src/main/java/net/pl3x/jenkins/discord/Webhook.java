package net.pl3x.jenkins.discord;

import hudson.model.BuildListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.json.JSONArray;
import org.json.JSONObject;

public class Webhook {
    public String url;

    public String username;
    public String avatar_url;
    public String content;
    public boolean tts;
    public Mentions allowed_mentions = new Mentions();
    public Embed embed = new Embed();

    public BuildListener listener;

    public Webhook(String url) {
        this.url = url;
    }

    public void send() {
        JSONObject json = new JSONObject();

        if (this.username != null && !this.username.isBlank()) {
            json.put("username", this.username);
        }
        if (this.avatar_url != null && !this.avatar_url.isBlank()) {
            json.put("avatar_url", this.avatar_url);
        }
        if (this.content != null && !this.content.isBlank()) {
            json.put("content", this.content);
        }
        if (this.tts) {
            json.put("tts", true);
        }
        JSONObject allowed_mentions = this.allowed_mentions.toJson();
        if (allowed_mentions != null) {
            json.put("allowed_mentions", allowed_mentions);
        }
        JSONObject embed = this.embed.toJson();
        if (embed != null) {
            json.put("embeds", new JSONArray().put(embed));
        }

        if (json.isEmpty()) {
            throw new RuntimeException("Nothing to send...");
        }

        String cleanedUpJson = json.toString().replace("\\\\", "\\");

        listener.getLogger().println("cleanedUpJson: " + cleanedUpJson);

        HttpResponse<JsonNode> response = Unirest.post(this.url).field("payload_json", cleanedUpJson).asJson();
        listener.getLogger().println("Response: " + response.getStatus() + " " + response.getBody());

        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new RuntimeException(response.getBody().getObject().toString(2));
        }
    }

    public static class Mentions {
        public List<Parse> parse = new ArrayList<>();
        public List<String> users = new ArrayList<>();
        public List<String> roles = new ArrayList<>();

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            if (!this.parse.isEmpty()) {
                json.put("parse", this.parse.stream()
                        .map(parse -> parse.name)
                        .collect(Collectors.toList())
                );
            }
            if (!this.users.isEmpty()) {
                json.put("users", this.users);
            }
            if (!this.roles.isEmpty()) {
                json.put("roles", this.roles);
            }
            return json.isEmpty() ? null : json;
        }

        public enum Parse {
            EVERYONE,
            USERS,
            ROLES;

            public final String name;

            Parse() {
                this.name = name().toLowerCase(Locale.ROOT);
            }
        }
    }

    public static class Embed {
        public int color = -1;
        public String title;
        public String url;
        public Author author = new Author();
        public String description;
        public Image image = new Image();
        public Image thumbnail = new Image();
        public List<Field> fields = new ArrayList<>();
        public Footer footer = new Footer();
        public String timestamp;

        public JSONObject toJson() {
            JSONObject json = new JSONObject();

            if (this.color != -1) {
                json.put("color", this.color);
            }
            if (this.title != null) {
                json.put("title", this.title);
            }
            if (this.url != null && !this.url.isBlank()) {
                if (this.title == null) {
                    json.put("title", "");
                }
                json.put("url", this.url);
            }
            JSONObject author = this.author.toJson();
            if (author != null) {
                json.put("author", author);
            }
            if (this.description != null && !this.description.isBlank()) {
                json.put("description", this.description);
            }
            JSONObject image = this.image.toJson();
            if (image != null) {
                json.put("image", image);
            }
            JSONObject thumbnail = this.thumbnail.toJson();
            if (thumbnail != null) {
                json.put("thumbnail", thumbnail);
            }
            List<JSONObject> fields = this.fields.stream().map(Field::toJson).collect(Collectors.toList());
            if (!fields.isEmpty()) {
                json.put("fields", new JSONArray().putAll(fields));
            }
            JSONObject footer = this.footer.toJson();
            if (footer != null) {
                json.put("footer", footer);
            }
            if (this.timestamp != null && !this.timestamp.isBlank()) {
                json.put("timestamp", this.timestamp);
            }

            return json.isEmpty() ? null : json;
        }

        public static class Author {
            public String name;
            public String url;
            public String icon_url;

            public JSONObject toJson() {
                JSONObject json = new JSONObject();

                if (this.name != null) {
                    json.put("name", this.name);
                }
                if (this.url != null && !this.url.isBlank()) {
                    if (this.name == null) {
                        json.put("name", "");
                    }
                    json.put("url", this.url);
                }
                if (this.icon_url != null) {
                    if (this.name == null) {
                        json.put("name", "");
                    }
                    json.put("icon_url", this.icon_url);
                }

                return json.isEmpty() ? null : json;
            }
        }

        public static class Field {
            public String name;
            public String value;
            public boolean inline;

            public Field(String name, String value) {
                this(name, value, false);
            }

            public Field(String name, String value, boolean inline) {
                this.name = name;
                this.value = value;
                this.inline = inline;
            }

            public JSONObject toJson() {
                JSONObject json = new JSONObject();

                // lets workaround https://github.com/discord/discord-api-docs/discussions/3227
                // by moving the name into the value to fake a header that supports markdown links
                if (this.name != null) {
                    if (this.value != null) {
                        this.value = String.format("%s%n%s", this.name, this.value);
                    } else {
                        this.value = String.format("%s", this.name);
                    }
                    this.name = null;
                }

                json.put("name", this.name == null ? "" : this.name);
                json.put("value", this.value == null ? "" : this.value);
                if (this.inline) {
                    json.put("inline", true);
                }

                return json.isEmpty() ? null : json;
            }
        }

        public static class Image {
            public String url;

            public JSONObject toJson() {
                JSONObject json = new JSONObject();
                if (this.url != null && !this.url.isBlank()) {
                    json.put("url", this.url);
                }
                return json.isEmpty() ? null : json;
            }
        }

        public static class Footer {
            public String text;
            public String icon_url;

            public JSONObject toJson() {
                JSONObject json = new JSONObject();

                if (this.text != null) {
                    json.put("text", this.text);
                }
                if (this.icon_url != null && !this.icon_url.isBlank()) {
                    if (this.text == null) {
                        json.put("text", "");
                    }
                    json.put("icon_url", this.icon_url);
                }

                return json.isEmpty() ? null : json;
            }
        }
    }
}
