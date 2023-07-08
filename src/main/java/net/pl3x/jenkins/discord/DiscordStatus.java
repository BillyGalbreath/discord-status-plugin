package net.pl3x.jenkins.discord;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.User;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;

public class DiscordStatus extends Notifier {
    public final String webhookUrl;
    public final String username;
    public final String avatar;
    public final String titleText;
    public final String titleIcon;
    public final String description;
    public final boolean showCommits;
    public final String commitHeader;
    public final String commitHeaderFallback;
    public final String commitBody;
    public final String footerText;
    public final String footerIcon;
    public final boolean footerTimestamp;
    public final String thumbnail;
    public final String image;

    @DataBoundConstructor
    public DiscordStatus(String webhookUrl, String username, String avatar, String titleText, String titleIcon, String description, boolean showCommits, String commitHeader, String commitHeaderFallback, String commitBody, String footerText, String footerIcon, boolean footerTimestamp, String thumbnail, String image) {
        this.webhookUrl = webhookUrl;
        this.username = username;
        this.avatar = avatar;
        this.titleText = titleText;
        this.titleIcon = titleIcon;
        this.description = description;
        this.showCommits = showCommits;
        this.commitHeader = commitHeader;
        this.commitHeaderFallback = commitHeaderFallback;
        this.commitBody = commitBody;
        this.footerText = footerText;
        this.footerIcon = footerIcon;
        this.footerTimestamp = footerTimestamp;
        this.thumbnail = thumbnail;
        this.image = image;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        Result buildResult = build.getResult();
        if (buildResult == null || !buildResult.isCompleteBuild()) {
            return true;
        }

        if (this.webhookUrl.isEmpty()) {
            return true;
        }

        //EnvVars env = build.getEnvironment(listener);
        AbstractProject<?, ?> project = build.getProject();
        Instant timestamp = build.getTime().toInstant();

        Map<String, String> buildData = new HashMap<>();
        buildData.put("build-number", build.getId());
        buildData.put("build-result", buildResult.toString().toLowerCase(Locale.ROOT));
        buildData.put("project-name", project.getDisplayName());
        buildData.put("project-desc", project.getDescription());
        buildData.put("timestamp", timestamp.toString());
        buildData.put("epoch-seconds", Long.toString(timestamp.getEpochSecond()));
        buildData.put("epoch-millis", Long.toString(timestamp.toEpochMilli()));

        Webhook webhook = new Webhook(this.webhookUrl);
        webhook.embed = new Webhook.Embed();
        webhook.listener = listener;

        if (buildResult.isBetterOrEqualTo(Result.SUCCESS)) {
            webhook.embed.color = 0x19A719;
        } else if (buildResult.isWorseThan(Result.SUCCESS)) {
            webhook.embed.color = 0xFFFF0A;
        } else if (buildResult.isWorseThan(Result.UNSTABLE)) {
            webhook.embed.color = 0xAC1A17;
        }

        if (this.avatar != null && !this.avatar.isEmpty()) {
            webhook.avatar_url = this.avatar;
        }

        if (this.username != null && !this.username.isEmpty()) {
            webhook.username = this.username;
        }

        if (this.titleIcon != null && !this.titleIcon.isEmpty()) {
            webhook.embed.author.icon_url = this.titleIcon;
        }

        if (this.titleText != null && !this.titleText.isEmpty()) {
            webhook.embed.author.name = replace(this.titleText, buildData);
        }

        if (this.thumbnail != null && !this.thumbnail.isEmpty()) {
            webhook.embed.thumbnail.url = this.thumbnail;
        }

        if (this.description != null && !this.description.isEmpty()) {
            webhook.embed.description = replace(this.description, buildData);
        }

        if (this.image != null && !this.image.isEmpty()) {
            webhook.embed.image.url = this.image;
        }

        if (this.footerIcon != null && !this.footerIcon.isEmpty()) {
            webhook.embed.footer.icon_url = this.footerIcon;
        }

        if (this.footerText != null && !this.footerText.isEmpty()) {
            webhook.embed.footer.text = replace(this.footerText, buildData);
        }

        if (this.footerTimestamp) {
            webhook.embed.timestamp = timestamp.toString();
        }

        if (this.showCommits) {
            build.getChangeSets().forEach(changeSet -> changeSet.forEach(entry -> {
                String hash = entry.getCommitId();
                String longHash = hash;
                if (hash.length() > 7) {
                    hash = hash.substring(0, 7);
                }

                String url = "";
                String header;
                try {
                    //noinspection unchecked
                    url = entry.getParent().getBrowser().getChangeSetLink(entry).toString();
                    header = this.commitHeader;
                } catch (Throwable e) {
                    header = this.commitHeaderFallback;
                }

                User author = entry.getAuthor();

                Map<String, String> commitData = new HashMap<>();
                commitData.put("commit-hash", hash);
                commitData.put("commit-longhash", longHash);
                commitData.put("commit-url", url);
                commitData.put("author-username", author.getId());
                commitData.put("author-fullname", author.getFullName());
                commitData.put("commit-message", entry.getMsg().trim());

                webhook.embed.fields.add(new Webhook.Embed.Field(
                        replace(header, commitData),
                        replace(this.commitBody, commitData)
                ));
            }));

            if (webhook.embed.fields.isEmpty()) {
                webhook.embed.fields.add(new Webhook.Embed.Field("", "*No Changes*\n"));
            }
        }

        try {
            listener.getLogger().println("Sending notification to Discord.");
            webhook.send();
        } catch (RuntimeException e) {
            e.printStackTrace(listener.getLogger());
        }

        return true;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public static String replace(String str, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            str = str.replace(String.format("{%s}", entry.getKey()), entry.getValue());
        }
        return str;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<hudson.tasks.Publisher> {
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public @NonNull String getDisplayName() {
            return "Discord Status";
        }

        public String getVersion() {
            return "1.0.0";
        }
    }
}
