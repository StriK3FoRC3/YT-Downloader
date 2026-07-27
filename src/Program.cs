using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

[assembly: AssemblyTitle("YT Downloader")]
[assembly: AssemblyDescription("YouTube video and playlist downloader")]
[assembly: AssemblyCompany("StriK3FoRC3")]
[assembly: AssemblyProduct("YT Downloader")]
[assembly: AssemblyVersion("1.0.0.0")]
[assembly: AssemblyFileVersion("1.0.0.0")]

namespace YTDownloader
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            AppWindowTheme.EnableForProcess();
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }

    internal static class AppWindowTheme
    {
        private enum PreferredAppMode
        {
            Default,
            AllowDark,
            ForceDark,
            ForceLight,
            Maximum
        }

        public static void EnableForProcess()
        {
            try
            {
                SetPreferredAppMode(PreferredAppMode.ForceDark);
                FlushMenuThemes();
            }
            catch { }
        }

        public static void ApplyToWindow(IntPtr handle, Color background, Color foreground, Color border)
        {
            if (handle == IntPtr.Zero) return;
            try
            {
                int enabled = 1;
                if (DwmSetWindowAttribute(handle, 20, ref enabled, sizeof(int)) != 0)
                    DwmSetWindowAttribute(handle, 19, ref enabled, sizeof(int));

                int borderColor = ColorTranslator.ToWin32(border);
                int captionColor = ColorTranslator.ToWin32(background);
                int textColor = ColorTranslator.ToWin32(foreground);
                DwmSetWindowAttribute(handle, 34, ref borderColor, sizeof(int));
                DwmSetWindowAttribute(handle, 35, ref captionColor, sizeof(int));
                DwmSetWindowAttribute(handle, 36, ref textColor, sizeof(int));
            }
            catch { }
        }

        [System.Runtime.InteropServices.DllImport("uxtheme.dll", EntryPoint = "#135")]
        private static extern PreferredAppMode SetPreferredAppMode(PreferredAppMode appMode);

        [System.Runtime.InteropServices.DllImport("uxtheme.dll", EntryPoint = "#136")]
        private static extern void FlushMenuThemes();

        [System.Runtime.InteropServices.DllImport("dwmapi.dll")]
        private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);
    }

    internal class DarkForm : Form
    {
        private Color chromeBorderColor;

        public Color ChromeBorderColor
        {
            get { return chromeBorderColor; }
            set
            {
                chromeBorderColor = value;
                if (IsHandleCreated) AppWindowTheme.ApplyToWindow(Handle, BackColor, ForeColor, chromeBorderColor);
            }
        }

        public DarkForm()
        {
            BackColor = Color.FromArgb(25, 25, 25);
            ForeColor = Color.FromArgb(245, 245, 245);
            chromeBorderColor = BackColor;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            AppWindowTheme.ApplyToWindow(Handle, BackColor, ForeColor, chromeBorderColor);
            base.OnHandleCreated(e);
        }
    }

    internal sealed class MainForm : DarkForm
    {
        private static readonly Color Bg = Color.FromArgb(25, 25, 25);
        private static readonly Color Panel = Color.FromArgb(35, 35, 35);
        private static readonly Color PanelLight = Color.FromArgb(44, 44, 44);
        private static readonly Color TextColor = Color.FromArgb(245, 245, 245);
        private static readonly Color Muted = Color.FromArgb(180, 180, 180);
        private static readonly Color Purple = Color.FromArgb(159, 0, 255);
        private static readonly Color PurpleHover = Color.FromArgb(181, 55, 255);
        private static readonly Color Green = Color.FromArgb(105, 210, 110);
        private static readonly Color Red = Color.FromArgb(244, 67, 54);
        private static readonly Color Blue = Color.FromArgb(82, 139, 255);
        private static readonly Color Yellow = Color.FromArgb(255, 235, 59);
        private static readonly Color Gray = Color.FromArgb(88, 88, 88);
        private readonly Font titleFont = new Font("Segoe UI", 20f, FontStyle.Bold, GraphicsUnit.Pixel);
        private readonly Font normalFont = new Font("Segoe UI", 14f, FontStyle.Regular, GraphicsUnit.Pixel);
        private readonly Font boldFont = new Font("Segoe UI", 14f, FontStyle.Bold, GraphicsUnit.Pixel);
        private readonly Font profileIndicatorFont = new Font("Segoe UI", 16f, FontStyle.Regular, GraphicsUnit.Pixel);
        private readonly Font profileIndicatorBoldFont = new Font("Segoe UI", 16f, FontStyle.Bold, GraphicsUnit.Pixel);

        private readonly TextBox urlBox = new TextBox();
        private Label subtitleLabel;
        private Label activeProfileLabel;
        private readonly DarkDropDown activeProfileBox = new DarkDropDown();
        private readonly RadioButton audioMode = new RadioButton();
        private readonly RadioButton videoMode = new RadioButton();
        private readonly DarkDropDown audioFormat = new DarkDropDown();
        private readonly DarkDropDown videoFormat = new DarkDropDown();
        private readonly DarkDropDown resolution = new DarkDropDown();
        private readonly DarkDropDown simultaneousDownloads = new DarkDropDown();
        private readonly DarkDropDown cookieBrowserBox = new DarkDropDown();
        private readonly DarkDropDown bitrateSettingsBox = new DarkDropDown();
        private readonly AccentCheckBox thumbnailToggle = new AccentCheckBox();
        private readonly AccentCheckBox titleFilterToggle = new AccentCheckBox();
        private readonly AccentCheckBox removeArtistPrefixToggle = new AccentCheckBox();
        private readonly AccentCheckBox metadataToggle = new AccentCheckBox();
        private readonly AccentCheckBox allMetadataFieldsToggle = new AccentCheckBox();
        private readonly AccentCheckBox metadataTitleToggle = new AccentCheckBox();
        private readonly AccentCheckBox metadataArtistToggle = new AccentCheckBox();
        private readonly AccentCheckBox metadataAlbumToggle = new AccentCheckBox();
        private readonly AccentCheckBox metadataDateToggle = new AccentCheckBox();
        private readonly AccentCheckBox metadataDescriptionToggle = new AccentCheckBox();
        private readonly AccentCheckBox metadataSourceToggle = new AccentCheckBox();
        private readonly AccentCheckBox metadataGenreToggle = new AccentCheckBox();
        private readonly AccentCheckBox metadataTrackToggle = new AccentCheckBox();
        private readonly AccentCheckBox metadataChaptersToggle = new AccentCheckBox();
        private readonly AccentCheckBox allTitleTagsToggle = new AccentCheckBox();
        private readonly DarkDropDown filterProfileBox = new DarkDropDown();
        private readonly TextBox profileNameBox = new TextBox();
        private readonly DarkRulesTextBox filterRulesBox = new DarkRulesTextBox();
        private readonly DarkVerticalScrollBar filterRulesScrollBar = new DarkVerticalScrollBar();
        private readonly ListView downloads = new ListView();
        private readonly ListView failures = new ListView();
        private readonly DarkTabControl resultTabs = new DarkTabControl();
        private readonly Label status = new Label();
        private Label speedLabel;
        private Label elapsedLabel;
        private readonly DarkProgressBar progress = new DarkProgressBar();
        private readonly StyledButton pasteButton;
        private readonly StyledButton clearLinksButton;
        private readonly StyledButton openButton;
        private readonly StyledButton downloadButton;
        private readonly StyledButton cancelButton;
        private readonly StyledButton clearFailedButton;
        private readonly StyledButton clearActivityButton;
        private readonly StyledButton infoButton;
        private readonly StyledButton updateButton;
        private readonly StyledButton settingsButton;
        private readonly StyledButton newProfileButton;
        private readonly StyledButton saveProfileButton;
        private readonly StyledButton deleteProfileButton;
        private readonly StyledButton closeSettingsButton;
        private readonly List<FilterProfile> filterProfiles = new List<FilterProfile>();
        private readonly List<TitleFilterOption> titleFilterOptions = new List<TitleFilterOption>();
        private readonly List<DarkHeaderWindow> darkHeaderWindows = new List<DarkHeaderWindow>();
        private readonly List<FailureRecord> sessionFailures = new List<FailureRecord>();

        private readonly string appDir;
        private readonly string dependenciesDir;
        private readonly string ytdlpPath;
        private readonly string ffmpegPath;
        private readonly string ffprobePath;
        private readonly string denoPath;
        private readonly string downloadFolder;
        private readonly string settingsPath;
        private readonly string logPath;
        private static readonly object LogLock = new object();
        private readonly object activeProcessesLock = new object();
        private readonly HashSet<Process> activeProcesses = new HashSet<Process>();
        private readonly Stopwatch sessionElapsed = new Stopwatch();
        private readonly System.Windows.Forms.Timer elapsedUiTimer = new System.Windows.Forms.Timer();
        private double currentCombinedSpeed;
        private CancellationTokenSource cancellation;
        private string sessionFailureReportPath;
        private bool sessionFailureReportWritten;
        private bool running;
        private bool suppressPreferences = true;
        private bool fittingColumns;
        private bool loadingProfileUi;
        private bool syncingProfileSelectors;
        private bool updatingSelectAll;
        private Form settingsWindow;
        private bool disposingSettings;

        public MainForm()
        {
            appDir = AppDomain.CurrentDomain.BaseDirectory;
            dependenciesDir = Path.Combine(appDir, "Dependencies");
            Directory.CreateDirectory(dependenciesDir);
            ytdlpPath = FindTool("yt-dlp.exe");
            ffmpegPath = FindTool("ffmpeg.exe");
            ffprobePath = FindTool("ffprobe.exe");
            denoPath = FindTool("deno.exe");
            downloadFolder = Path.Combine(appDir, "Downloads");
            settingsPath = Path.Combine(dependenciesDir, "settings.ini");
            logPath = Path.Combine(dependenciesDir, "YTD.log");

            pasteButton = new StyledButton("Paste", Blue, boldFont);
            clearLinksButton = new StyledButton("Clear", Red, boldFont);
            openButton = new StyledButton("Open Downloads", Blue, boldFont);
            downloadButton = new StyledButton("Download", Green, boldFont);
            cancelButton = new StyledButton("Cancel", Red, boldFont);
            clearFailedButton = new StyledButton("Clear failed", Red, boldFont);
            clearActivityButton = new StyledButton("Clear", Red, boldFont);
            infoButton = new StyledButton("Info", Yellow, boldFont);
            updateButton = new StyledButton("Check For Updates", Blue, boldFont);
            settingsButton = new StyledButton("Settings", Purple, boldFont);
            newProfileButton = new StyledButton("New profile", Purple, boldFont);
            saveProfileButton = new StyledButton("Save profile", Green, boldFont);
            deleteProfileButton = new StyledButton("Delete", Red, boldFont);
            closeSettingsButton = new StyledButton("Close", Gray, boldFont);

            Text = "YT Downloader";
            MinimumSize = new Size(800, 550);
            Size = new Size(800, 550);
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Bg;
            ForeColor = TextColor;
            Font = normalFont;
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            BuildUi();
            elapsedUiTimer.Interval = 250;
            elapsedUiTimer.Tick += delegate { UpdateSpeedAndElapsedDisplay(); };
            LoadPreferences();
            EnsureColumnsFit(downloads);
            EnsureColumnsFit(failures);
            suppressPreferences = false;
            WireEvents();
            UpdateMode();
            UpdateToolStatus();
            WriteLog("Application started. Version 1.0.0. Cookie setting: " + cookieBrowserBox.Text + ". Detected default: " + DetectDefaultBrowserForCookies());
        }

        private string FindTool(string name)
        {
            return Path.Combine(dependenciesDir, name);
        }

        private void BuildUi()
        {
            Label title = MakeLabel("YT Downloader", titleFont, TextColor);
            subtitleLabel = MakeLabel("Download videos, songs, and complete playlists in the format you want.", normalFont, Muted);
            activeProfileLabel = MakeLabel("Profile:", profileIndicatorFont, Purple);
            activeProfileLabel.TextAlign = ContentAlignment.MiddleRight;
            activeProfileBox.Font = profileIndicatorBoldFont;
            activeProfileBox.ForeColor = Green;
            activeProfileBox.CenterText = true;
            Label urlLabel = MakeLabel("YouTube links (one per line)", boldFont, TextColor);
            Label typeLabel = MakeLabel("Download type", boldFont, TextColor);
            Label formatLabel = MakeLabel("Format", boldFont, TextColor);
            Label resolutionLabel = MakeLabel("Resolution", boldFont, TextColor);
            Label simultaneousLabel = MakeLabel("Downloads", boldFont, TextColor);
            speedLabel = MakeLabel("-- MB/s", boldFont, Muted);
            speedLabel.TextAlign = ContentAlignment.MiddleCenter;
            elapsedLabel = MakeLabel("00:00", boldFont, Muted);
            elapsedLabel.TextAlign = ContentAlignment.MiddleCenter;

            urlBox.Multiline = true;
            urlBox.ScrollBars = ScrollBars.None;
            urlBox.BorderStyle = BorderStyle.None;
            urlBox.BackColor = Panel;
            urlBox.ForeColor = TextColor;
            urlBox.Font = normalFont;
            urlBox.AcceptsReturn = true;

            ConfigureRadio(audioMode, "Audio only");
            ConfigureRadio(videoMode, "Video");
            audioMode.Checked = true;
            ConfigureCombo(audioFormat, new object[] { "MP3", "M4A", "FLAC", "WAV", "OGG", "OPUS", "AAC" }, 0);
            ConfigureCombo(videoFormat, new object[] { "MP4", "MKV", "WEBM" }, 0);
            ConfigureCombo(resolution, new object[] { "Highest", "4320p (8K)", "2160p (4K)", "1440p", "1080p", "720p", "480p", "360p" }, 0);
            ConfigureCombo(simultaneousDownloads, new object[] { "1 at once", "2 at once", "3 at once", "4 at once", "5 at once" }, 1);
            audioFormat.CenterText = true;
            videoFormat.CenterText = true;
            resolution.CenterText = true;
            simultaneousDownloads.CenterText = true;
            ConfigureCombo(cookieBrowserBox, new object[] { "Automatic", "Firefox", "Chrome", "Edge", "Brave", "Opera", "Vivaldi", "Chromium", "Disabled" }, 0);
            ConfigureCombo(bitrateSettingsBox, new object[] { "Automatic", "320 kbps", "256 kbps", "224 kbps", "192 kbps", "160 kbps", "128 kbps", "96 kbps", "64 kbps", "48 kbps" }, 0);
            bitrateSettingsBox.CenterText = true;
            ConfigureCheck(thumbnailToggle, "Embed thumbnail", true);
            ConfigureCheck(titleFilterToggle, "Title Cleanup", true);
            ConfigureCheck(removeArtistPrefixToggle, "Remove artist prefix (Artist - Title)", false);
            ConfigureCheck(metadataToggle, "Metadata", true);
            ConfigureCheck(allMetadataFieldsToggle, "All", false);
            ConfigureCheck(metadataTitleToggle, "Title", true);
            ConfigureCheck(metadataArtistToggle, "Artist / uploader", true);
            ConfigureCheck(metadataAlbumToggle, "Album / series", true);
            ConfigureCheck(metadataDateToggle, "Date", true);
            ConfigureCheck(metadataDescriptionToggle, "Description", true);
            ConfigureCheck(metadataSourceToggle, "Source URL", true);
            ConfigureCheck(metadataGenreToggle, "Genre", true);
            ConfigureCheck(metadataTrackToggle, "Track / disc", true);
            ConfigureCheck(metadataChaptersToggle, "Chapters", true);
            ConfigureCheck(allTitleTagsToggle, "All", false);

            ConfigureList(downloads, new[] { "Title", "Type", "Status", "Source link" });
            ConfigureList(failures, new[] { "Title", "Reason", "Source link" });
            failures.DoubleClick += delegate { OpenSelectedFailure(); };

            TabPage activityPage = MakeTab("Activity");
            TabPage failedPage = MakeTab("Failed downloads (0)");
            activityPage.Controls.Add(downloads);
            failedPage.Controls.Add(failures);
            failedPage.Controls.Add(clearFailedButton);
            activityPage.Resize += delegate
            {
                int w = activityPage.ClientSize.Width;
                int h = activityPage.ClientSize.Height;
                downloads.SetBounds(0, 0, w, Math.Max(50, h));
                EnsureColumnsFit(downloads);
            };
            failedPage.Resize += delegate
            {
                int w = failedPage.ClientSize.Width;
                int h = failedPage.ClientSize.Height;
                failures.SetBounds(0, 0, w, Math.Max(50, h - 42));
                clearFailedButton.SetBounds(Math.Max(0, w - 133), Math.Max(0, h - 38), 125, 32);
                EnsureColumnsFit(failures);
            };
            resultTabs.TabPages.Add(activityPage);
            resultTabs.TabPages.Add(failedPage);
            resultTabs.Appearance = TabAppearance.FlatButtons;
            resultTabs.ItemSize = new Size(150, 28);
            resultTabs.SizeMode = TabSizeMode.Fixed;
            resultTabs.BackColor = Bg;
            resultTabs.ForeColor = TextColor;

            status.Text = "Ready.";
            status.ForeColor = Muted;
            status.AutoEllipsis = true;
            progress.ForeColor = Purple;
            progress.BackColor = Panel;

            Controls.AddRange(new Control[] { title, infoButton, updateButton, subtitleLabel, activeProfileLabel, activeProfileBox, urlLabel, urlBox, clearLinksButton, pasteButton, typeLabel,
                audioMode, videoMode, formatLabel, audioFormat, videoFormat, resolutionLabel, resolution, simultaneousLabel, simultaneousDownloads,
                openButton, resultTabs, status, speedLabel, elapsedLabel, progress, settingsButton, clearActivityButton,
                downloadButton, cancelButton });

            int titleWidth = TextRenderer.MeasureText(title.Text, title.Font, new Size(int.MaxValue, int.MaxValue), TextFormatFlags.NoPadding).Width;
            title.SetBounds(20, 10, titleWidth, 28);
            infoButton.SetBounds(title.Right + 10, 8, 100, 32);
            updateButton.SetBounds(infoButton.Right + 10, 8, 150, 32);
            subtitleLabel.SetBounds(20, 42, 680, 21);
            activeProfileLabel.SetBounds(560, 16, 52, 28);
            activeProfileBox.SetBounds(618, 16, 100, 28);
            settingsButton.SetBounds(730, 14, 90, 32);
            urlLabel.SetBounds(20, 68, 260, 21);
            urlBox.SetBounds(20, 92, 800, 62);
            clearLinksButton.SetBounds(630, 58, 90, 32);
            pasteButton.SetBounds(730, 58, 90, 32);
            typeLabel.SetBounds(20, 168, 150, 21);
            audioMode.SetBounds(20, 192, 110, 26);
            videoMode.SetBounds(135, 192, 80, 26);
            formatLabel.SetBounds(220, 168, 100, 21);
            audioFormat.SetBounds(220, 191, 105, 28);
            videoFormat.SetBounds(220, 191, 105, 28);
            resolutionLabel.SetBounds(340, 168, 130, 21);
            resolution.SetBounds(340, 191, 130, 28);
            simultaneousLabel.SetBounds(485, 168, 105, 21);
            simultaneousDownloads.SetBounds(485, 191, 105, 28);
            openButton.SetBounds(604, 189, 160, 32);
            resultTabs.SetBounds(20, 236, 744, 215);
            downloads.SetBounds(0, 0, 842, 195);
            downloads.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
            clearActivityButton.SetBounds(640, 622, 90, 32);
            failures.SetBounds(0, 0, 842, 195);
            failures.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
            clearFailedButton.SetBounds(700, 202, 125, 32);
            clearFailedButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
            status.SetBounds(20, 608, 580, 23);
            speedLabel.SetBounds(500, 598, 130, 20);
            elapsedLabel.SetBounds(640, 598, 90, 20);
            progress.SetBounds(20, 636, 580, 18);
            downloadButton.SetBounds(500, 622, 130, 32);
            cancelButton.SetBounds(740, 622, 90, 32);
            cancelButton.Enabled = false;
            BuildSettingsWindow();
            Resize += delegate { LayoutResponsive(); };
            LayoutResponsive();
        }

        private void BuildSettingsWindow()
        {
            settingsWindow = new DarkForm();
            settingsWindow.Text = "YT Downloader Settings";
            settingsWindow.Size = new Size(740, 672);
            settingsWindow.MinimumSize = new Size(720, 652);
            settingsWindow.StartPosition = FormStartPosition.Manual;
            settingsWindow.BackColor = Bg;
            settingsWindow.ForeColor = TextColor;
            settingsWindow.Font = normalFont;
            settingsWindow.Icon = Icon;
            settingsWindow.ShowInTaskbar = false;
            settingsWindow.FormClosing += delegate(object sender, FormClosingEventArgs e)
            {
                if (disposingSettings || e.CloseReason != CloseReason.UserClosing) return;
                e.Cancel = true;
                settingsWindow.Hide();
                SavePreferences();
            };
            Label heading = MakeLabel("Settings", titleFont, TextColor);
            Label optionsHeading = MakeLabel("Profile download options", boldFont, TextColor);
            Label metadataLabel = MakeLabel("Metadata to embed", boldFont, TextColor);
            Label cookieBrowserLabel = MakeLabel("Cookies from", boldFont, TextColor);
            Label profileLabel = MakeLabel("Filter profile", boldFont, TextColor);
            Label nameLabel = MakeLabel("Profile name", boldFont, TextColor);
            Label bitrateLabel = MakeLabel("Audio Bitrate Settings", boldFont, TextColor);
            Label cleanupLabel = MakeLabel("Remove bracketed title tags", boldFont, TextColor);
            Label helpLabel = MakeLabel("Selected tags are removed only when found inside (parentheses) or [square brackets], preserving the artist and real title.", normalFont, Muted);
            Label customLabel = MakeLabel("Custom bracketed tags (one phrase per line)", boldFont, TextColor);
            LinkLabel customHelpLink = new LinkLabel();
            customHelpLink.Text = "What should I enter?";
            customHelpLink.Font = normalFont;
            customHelpLink.LinkColor = PurpleHover;
            customHelpLink.ActiveLinkColor = TextColor;
            customHelpLink.VisitedLinkColor = PurpleHover;
            customHelpLink.LinkBehavior = LinkBehavior.HoverUnderline;
            customHelpLink.BackColor = Bg;
            customHelpLink.TextAlign = ContentAlignment.MiddleLeft;
            customHelpLink.Cursor = Cursors.Hand;
            customHelpLink.LinkClicked += delegate { ShowTitleTagHelp(); };
            foreach (Label label in new[] { heading, optionsHeading, metadataLabel, cookieBrowserLabel, profileLabel, nameLabel, bitrateLabel, cleanupLabel, helpLabel, customLabel }) label.BackColor = Bg;

            filterProfileBox.Font = normalFont;
            profileNameBox.BorderStyle = BorderStyle.None;
            profileNameBox.BackColor = PanelLight;
            profileNameBox.ForeColor = TextColor;
            profileNameBox.Font = normalFont;
            profileNameBox.Multiline = true;
            profileNameBox.MaxLength = 25;
            profileNameBox.AcceptsReturn = false;
            profileNameBox.ScrollBars = ScrollBars.None;
            profileNameBox.KeyDown += delegate(object sender, KeyEventArgs e)
            {
                if (e.KeyCode == Keys.Enter) { e.SuppressKeyPress = true; e.Handled = true; }
            };
            filterRulesBox.Multiline = true;
            filterRulesBox.ScrollBars = RichTextBoxScrollBars.None;
            filterRulesBox.WordWrap = false;
            filterRulesBox.DetectUrls = false;
            filterRulesBox.BorderStyle = BorderStyle.None;
            filterRulesBox.BackColor = PanelLight;
            filterRulesBox.ForeColor = TextColor;
            filterRulesBox.Font = normalFont;

            settingsWindow.Controls.AddRange(new Control[] { heading, profileLabel, filterProfileBox, nameLabel, profileNameBox, bitrateLabel, bitrateSettingsBox,
                optionsHeading, thumbnailToggle, titleFilterToggle, metadataToggle, cookieBrowserLabel, cookieBrowserBox, metadataLabel,
                allMetadataFieldsToggle,
                metadataTitleToggle, metadataArtistToggle, metadataAlbumToggle, metadataDateToggle, metadataDescriptionToggle,
                metadataSourceToggle, metadataGenreToggle, metadataTrackToggle, metadataChaptersToggle,
                cleanupLabel, allTitleTagsToggle, removeArtistPrefixToggle, helpLabel, customLabel, customHelpLink, filterRulesBox, filterRulesScrollBar,
                newProfileButton, saveProfileButton, deleteProfileButton, closeSettingsButton });
            titleFilterOptions.Clear();
            AddTitleFilterOption("Official video", new[] { "Official video" }, 18, 230, 160);
            AddTitleFilterOption("Official music video", new[] { "Official music video" }, 230, 230, 190);
            AddTitleFilterOption("Official audio", new[] { "Official audio" }, 470, 230, 150);
            AddTitleFilterOption("Lyrics", new[] { "Lyrics" }, 18, 258, 160);
            AddTitleFilterOption("Lyric video", new[] { "Lyric video" }, 230, 258, 190);
            AddTitleFilterOption("Music video", new[] { "Music video" }, 470, 258, 150);
            AddTitleFilterOption("Visualizer", new[] { "Visualizer" }, 18, 286, 160);
            AddTitleFilterOption("Audio", new[] { "Audio" }, 230, 286, 190);
            AddTitleFilterOption("HD / 4K", new[] { "HD", "4K" }, 470, 286, 150);
            heading.SetBounds(18, 12, 240, 28);
            profileLabel.SetBounds(18, 50, 130, 21);
            filterProfileBox.SetBounds(18, 75, 220, 28);
            nameLabel.SetBounds(255, 50, 130, 21);
            profileNameBox.SetBounds(255, 78, 234, 22);
            bitrateLabel.SetBounds(510, 50, 194, 21);
            bitrateSettingsBox.SetBounds(510, 75, 194, 28);
            optionsHeading.SetBounds(18, 116, 200, 21);
            thumbnailToggle.SetBounds(18, 140, 165, 25);
            metadataToggle.SetBounds(195, 140, 130, 25);
            titleFilterToggle.SetBounds(360, 140, 150, 25);
            cookieBrowserLabel.SetBounds(540, 116, 150, 21);
            cookieBrowserBox.SetBounds(540, 140, 164, 28);
            metadataLabel.SetBounds(18, 180, 180, 21);
            allMetadataFieldsToggle.SetBounds(200, 178, 70, 25);
            metadataTitleToggle.SetBounds(18, 204, 170, 25);
            metadataArtistToggle.SetBounds(245, 204, 180, 25);
            metadataAlbumToggle.SetBounds(490, 204, 180, 25);
            metadataDateToggle.SetBounds(18, 232, 170, 25);
            metadataDescriptionToggle.SetBounds(245, 232, 180, 25);
            metadataSourceToggle.SetBounds(490, 232, 180, 25);
            metadataGenreToggle.SetBounds(18, 260, 170, 25);
            metadataTrackToggle.SetBounds(245, 260, 180, 25);
            metadataChaptersToggle.SetBounds(490, 260, 180, 25);
            cleanupLabel.SetBounds(18, 298, 195, 21);
            allTitleTagsToggle.SetBounds(220, 296, 70, 25);
            removeArtistPrefixToggle.SetBounds(360, 296, 344, 25);
            helpLabel.SetBounds(18, 321, 690, 36);
            foreach (TitleFilterOption option in titleFilterOptions) option.CheckBox.Top += 130;
            customLabel.SetBounds(18, 443, 500, 21);
            customHelpLink.SetBounds(18, 592, 202, 18);
            filterRulesBox.SetBounds(18, 468, 704, 111);
            filterRulesScrollBar.SetBounds(708, 468, 14, 111);
            filterRulesScrollBar.Visible = false;
            filterRulesScrollBar.ValueChanged += delegate
            {
                if (!filterRulesBox.IsHandleCreated) return;
                int currentLine = SendMessage(filterRulesBox.Handle, 0x00CE, IntPtr.Zero, IntPtr.Zero).ToInt32();
                int difference = filterRulesScrollBar.Value - currentLine;
                if (difference != 0) SendMessage(filterRulesBox.Handle, 0x00B6, IntPtr.Zero, new IntPtr(difference));
                UpdateFilterRulesScrollBar();
            };
            filterRulesBox.VScroll += delegate { UpdateFilterRulesScrollBar(); };
            filterRulesBox.SelectionChanged += delegate { UpdateFilterRulesScrollBar(); };
            filterRulesBox.ScrollPositionChanged += delegate { UpdateFilterRulesScrollBar(); };
            filterRulesBox.TextChanged += delegate { UpdateFilterRulesScrollBar(); };
            newProfileButton.SetBounds(267, 590, 120, 32);
            saveProfileButton.SetBounds(397, 590, 130, 32);
            deleteProfileButton.SetBounds(537, 590, 85, 32);
            closeSettingsButton.SetBounds(632, 590, 85, 32);
            settingsWindow.Paint += delegate(object sender, PaintEventArgs e)
            {
                Rectangle profileOutline = profileNameBox.Bounds;
                profileOutline.Inflate(2, 2);
                DrawRoundedOutline(e.Graphics, profileOutline, 8, Purple);
                int rulesWidth = Math.Max(100, settingsWindow.ClientSize.Width - 36);
                Rectangle rulesOutline = new Rectangle(filterRulesBox.Left, filterRulesBox.Top, rulesWidth, filterRulesBox.Height);
                rulesOutline.Inflate(2, 2);
                DrawRoundedOutline(e.Graphics, rulesOutline, 8, Purple);
            };
            settingsWindow.Resize += delegate
            {
                int w = settingsWindow.ClientSize.Width;
                int h = settingsWindow.ClientSize.Height;
                int bitrateLeft = w - 214;
                bitrateLabel.Left = bitrateLeft;
                bitrateSettingsBox.Left = bitrateLeft;
                profileNameBox.Width = Math.Max(160, bitrateLeft - profileNameBox.Left - 21);
                cookieBrowserLabel.Left = w - 182;
                cookieBrowserBox.Left = w - 182;
                helpLabel.Width = w - 36;
                customHelpLink.Left = 18;
                customHelpLink.Width = 202;
                filterRulesBox.Height = Math.Max(80, h - 522);
                UpdateFilterRulesScrollBar();
                int buttonTop = filterRulesBox.Bottom + 6;
                customHelpLink.Top = buttonTop + 7;
                newProfileButton.SetBounds(w - 475, buttonTop + 5, 120, 32);
                saveProfileButton.SetBounds(w - 345, buttonTop + 5, 130, 32);
                deleteProfileButton.SetBounds(w - 200, buttonTop + 5, 85, 32);
                closeSettingsButton.SetBounds(w - 105, buttonTop + 5, 85, 32);
                ApplyRoundedRegion(filterProfileBox, 8);
                ApplyRoundedRegion(cookieBrowserBox, 8);
                ApplyRoundedRegion(bitrateSettingsBox, 8);
                ApplyRoundedRegion(profileNameBox, 8);
                ApplyRoundedRegion(filterRulesBox, 8);
            };
        }

        private void AddTitleFilterOption(string label, string[] terms, int left, int top, int width)
        {
            AccentCheckBox box = new AccentCheckBox();
            ConfigureCheck(box, label, false);
            box.SetBounds(left, top, width, 25);
            settingsWindow.Controls.Add(box);
            titleFilterOptions.Add(new TitleFilterOption(box, terms));
        }

        private void CenterSettingsWindow()
        {
            Rectangle working = Screen.FromControl(this).WorkingArea;
            int x = Left + (Width - settingsWindow.Width) / 2;
            int y = Top + (Height - settingsWindow.Height) / 2;
            x = Math.Max(working.Left, Math.Min(x, working.Right - settingsWindow.Width));
            y = Math.Max(working.Top, Math.Min(y, working.Bottom - settingsWindow.Height));
            settingsWindow.Location = new Point(x, y);
        }

        private void ShowAppInfo()
        {
            using (DarkForm info = new DarkForm())
            {
                info.Text = "YT Downloader Info";
                info.ClientSize = new Size(740, 620);
                info.FormBorderStyle = FormBorderStyle.FixedDialog;
                info.MaximizeBox = false;
                info.MinimizeBox = false;
                info.ShowInTaskbar = false;
                info.StartPosition = FormStartPosition.CenterParent;
                info.BackColor = Bg;
                info.ForeColor = TextColor;
                info.ChromeBorderColor = Purple;
                info.Font = normalFont;
                info.Icon = Icon;
                info.KeyPreview = true;

                Label heading = MakeLabel("Format and quality guide", titleFont, TextColor);
                heading.SetBounds(20, 16, 400, 30);

                Label recommendationsHeading = MakeLabel("Recommended choices", boldFont, PurpleHover);
                Label recommendationsBody = MakeLabel(
                    "Best audio quality per file size: OPUS + Automatic Audio Bitrate.\r\n" +
                    "Best audio compatibility: M4A + Automatic Audio Bitrate.\r\n" +
                    "Best legacy compatibility: MP3 + Automatic Audio Bitrate.\r\n" +
                    "Best general video compatibility: MP4 + Highest.\r\n" +
                    "Best flexible video container: MKV + Highest.",
                    normalFont, Muted);
                int nextSectionTop = PlaceInfoSection(recommendationsHeading, recommendationsBody, 58, 700);

                Label bitrateHeading = MakeLabel("What Automatic Audio Bitrate does", boldFont, PurpleHover);
                Label bitrateBody = MakeLabel(
                    "Automatic Audio Bitrate checks the source codec and bitrate; it is not simply a maximum setting.\r\n" +
                    "Matching M4A/AAC, OPUS, OGG/Vorbis, or MP3 audio is preserved without another lossy conversion.\r\n" +
                    "Conversions use MP3 at 192/256/320 kbps or other lossy formats at 96/160/224/256 kbps, chosen from the source.\r\n" +
                    "A fixed rate forces lossy conversion and may enlarge the file without increasing its real quality.",
                    normalFont, Muted);
                nextSectionTop = PlaceInfoSection(bitrateHeading, bitrateBody, nextSectionTop, 700);

                Label audioHeading = MakeLabel("Audio formats", boldFont, PurpleHover);
                Label audioBody = MakeLabel(
                    "OPUS gives excellent quality per file size but has less legacy support. M4A/AAC is the best general choice. MP3 suits older devices but normally requires conversion. FLAC/WAV is much larger and cannot restore quality lost by YouTube. OGG and raw AAC are mainly for specific workflows.",
                    normalFont, Muted);
                nextSectionTop = PlaceInfoSection(audioHeading, audioBody, nextSectionTop, 700);

                Label videoHeading = MakeLabel("Video formats and resolution", boldFont, PurpleHover);
                Label videoBody = MakeLabel(
                    "MP4 has the widest device and editor support. MKV supports the most codec combinations. WEBM is intended for modern web codecs but is less compatible. Highest selects YouTube's highest resolution, including 8K when available; choosing a value limits resolution and never upscales.",
                    normalFont, Muted);
                nextSectionTop = PlaceInfoSection(videoHeading, videoBody, nextSectionTop, 700);

                Label updateHeading = MakeLabel("Check For Updates button", boldFont, Blue);
                Label updateBody = MakeLabel(
                    "Checks yt-dlp, Deno, FFmpeg, and ffprobe only when you press it. yt-dlp and Deno use their official self-updaters. FFmpeg and ffprobe use the latest stable Gyan Windows package linked by FFmpeg's official download page, and its published SHA-256 checksum is verified before replacement. YT Downloader itself is not updated because it has no release server. Results appear in the status line and YTD.log.",
                    normalFont, Muted);
                nextSectionTop = PlaceInfoSection(updateHeading, updateBody, nextSectionTop, 700);

                Label warningHeading = MakeLabel("Cookies, privacy, and account safety", boldFont, Yellow);
                Label warningBody = MakeLabel(
                    "Downloading 5 at once reaches YouTube's request limits faster and can cause failures or temporary blocking. Use 1 or 2 at once unless more is necessary.\r\n" +
                    "With Automatic or a selected browser, the app tells yt-dlp to read that browser's cookies locally. The app itself never reads, saves, uploads, or logs the cookie values.\r\n" +
                    "Relevant cookies are sent only to YouTube/Google download services as part of the authenticated requests. They are not sent to the app developer or an unrelated service.\r\n" +
                    "YouTube can still associate downloads with the signed-in account. yt-dlp warns that the account could be banned temporarily or permanently and suggests considering a throwaway account.",
                    normalFont, Muted);
                nextSectionTop = PlaceInfoSection(warningHeading, warningBody, nextSectionTop, 700);

                LinkLabel accountWarningLink = new LinkLabel();
                accountWarningLink.Text = "Open the official yt-dlp cookie and account warning";
                accountWarningLink.Font = normalFont;
                accountWarningLink.LinkColor = Yellow;
                accountWarningLink.ActiveLinkColor = TextColor;
                accountWarningLink.VisitedLinkColor = Yellow;
                accountWarningLink.LinkBehavior = LinkBehavior.HoverUnderline;
                accountWarningLink.BackColor = Bg;
                accountWarningLink.SetBounds(20, nextSectionTop, 350, 22);
                accountWarningLink.LinkClicked += delegate
                {
                    try { Process.Start("https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies"); }
                    catch { }
                };

                StyledButton close = new StyledButton("Close", Gray, boldFont);
                close.SetBounds(620, accountWarningLink.Bottom + 10, 100, 32);
                info.ClientSize = new Size(740, close.Bottom + 18);
                close.Click += delegate { info.Close(); };
                info.KeyDown += delegate(object sender, KeyEventArgs e)
                {
                    if (e.KeyCode == Keys.Escape) { e.SuppressKeyPress = true; info.Close(); }
                };

                info.Controls.AddRange(new Control[] { heading, recommendationsHeading, recommendationsBody,
                    bitrateHeading, bitrateBody, audioHeading, audioBody, videoHeading, videoBody,
                    updateHeading, updateBody, warningHeading, warningBody, accountWarningLink, close });
                info.ShowDialog(this);
            }
        }

        private static int PlaceInfoSection(Label heading, Label body, int top, int width)
        {
            heading.SetBounds(20, top, width, 22);
            Size measured = TextRenderer.MeasureText(body.Text, body.Font, new Size(width, int.MaxValue),
                TextFormatFlags.WordBreak | TextFormatFlags.NoPrefix | TextFormatFlags.NoPadding);
            body.SetBounds(20, heading.Bottom + 2, width, measured.Height + 8);
            return body.Bottom + 10;
        }

        private void ShowTitleTagHelp()
        {
            using (DarkForm help = new DarkForm())
            {
                help.Text = "Custom bracketed tags help";
                help.ClientSize = new Size(560, 400);
                help.FormBorderStyle = FormBorderStyle.FixedDialog;
                help.MaximizeBox = false;
                help.MinimizeBox = false;
                help.ShowInTaskbar = false;
                help.StartPosition = FormStartPosition.CenterParent;
                help.BackColor = Bg;
                help.ForeColor = TextColor;
                help.ChromeBorderColor = Purple;
                help.Font = normalFont;
                help.Icon = Icon;

                Label heading = MakeLabel("Custom bracketed tags", titleFont, TextColor);
                heading.SetBounds(20, 16, 400, 30);
                Label explanation = MakeLabel(
                    "Enter one phrase per line without surrounding ( ) or [ ].\r\n\r\n" +
                    "Matching is case-insensitive. When a bracketed group contains your phrase, that entire group is removed.\r\n\r\n" +
                    "Example entries:\r\nOfficial Performance\r\n4K Remaster\r\n\r\n" +
                    "Artist - Song [Official Performance] (4K Remaster)\r\nbecomes:\r\nArtist - Song\r\n\r\n" +
                    "Enter normal text, not regular expressions. Title Cleanup must be enabled.", normalFont, Muted);
                explanation.SetBounds(20, 58, 520, 260);

                LinkLabel documentation = new LinkLabel();
                documentation.Text = "Open official yt-dlp metadata documentation";
                documentation.Font = normalFont;
                documentation.LinkColor = PurpleHover;
                documentation.ActiveLinkColor = TextColor;
                documentation.VisitedLinkColor = PurpleHover;
                documentation.LinkBehavior = LinkBehavior.HoverUnderline;
                documentation.BackColor = Bg;
                documentation.SetBounds(20, 326, 350, 24);
                documentation.LinkClicked += delegate
                {
                    try { Process.Start("https://github.com/yt-dlp/yt-dlp#modifying-metadata"); }
                    catch { }
                };

                StyledButton close = new StyledButton("Close", Gray, boldFont);
                close.SetBounds(440, 354, 100, 32);
                close.Click += delegate { help.Close(); };
                help.Controls.AddRange(new Control[] { heading, explanation, documentation, close });
                help.ShowDialog(settingsWindow);
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            DrawRoundedOutline(e.Graphics, urlBox.Bounds, 8, Purple);
            DrawRoundedOutline(e.Graphics, resultTabs.Bounds, 9, Color.FromArgb(92, 92, 92));
            DrawRoundedOutline(e.Graphics, progress.Bounds, 6, Color.FromArgb(92, 92, 92));
        }

        private void LayoutResponsive()
        {
            int width = ClientSize.Width;
            int bottom = ClientSize.Height;
            urlBox.Width = width - 40;
            pasteButton.Left = width - 110;
            clearLinksButton.Left = pasteButton.Left - 100;
            settingsButton.Left = width - 110;
            LayoutProfileIndicator();
            subtitleLabel.Width = Math.Max(100, settingsButton.Left - subtitleLabel.Left - 12);
            openButton.Left = width - 180;
            resultTabs.Width = width - 40;
            resultTabs.Height = Math.Max(180, bottom - 316);
            status.Top = bottom - 69;
            status.Width = Math.Max(180, width - 390);
            progress.Top = bottom - 41;
            progress.Width = Math.Max(180, width - 390);
            downloadButton.Left = width - 350;
            downloadButton.Top = bottom - 48;
            speedLabel.SetBounds(downloadButton.Left, downloadButton.Top - 24, downloadButton.Width, 20);
            clearActivityButton.Left = width - 210;
            clearActivityButton.Top = bottom - 48;
            elapsedLabel.SetBounds(clearActivityButton.Left, clearActivityButton.Top - 24, clearActivityButton.Width, 20);
            cancelButton.Left = width - 110;
            cancelButton.Top = bottom - 48;
            ApplyRoundedRegion(urlBox, 8);
            ApplyRoundedRegion(audioFormat, 8);
            ApplyRoundedRegion(videoFormat, 8);
            ApplyRoundedRegion(resolution, 8);
            ApplyRoundedRegion(simultaneousDownloads, 8);
            ApplyRoundedRegion(activeProfileBox, 8);
            ApplyRoundedRegion(downloads, 8);
            ApplyRoundedRegion(failures, 8);
            ApplyRoundedRegion(resultTabs, 9);
            ApplyRoundedRegion(progress, 6);
            infoButton.BringToFront();
            updateButton.BringToFront();
            settingsButton.BringToFront();
            activeProfileBox.BringToFront();
            clearLinksButton.BringToFront();
            pasteButton.BringToFront();
            ApplyRoundedRegion(filterProfileBox, 8);
            ApplyRoundedRegion(cookieBrowserBox, 8);
            ApplyRoundedRegion(bitrateSettingsBox, 8);
            ApplyRoundedRegion(profileNameBox, 8);
            ApplyRoundedRegion(filterRulesBox, 8);
            Invalidate();
        }

        private void WireEvents()
        {
            audioMode.CheckedChanged += delegate { UpdateMode(); };
            videoMode.CheckedChanged += delegate { UpdateMode(); };
            audioFormat.SelectedIndexChanged += delegate { SavePreferences(); };
            videoFormat.SelectedIndexChanged += delegate { SavePreferences(); };
            resolution.SelectedIndexChanged += delegate { SavePreferences(); };
            simultaneousDownloads.SelectedIndexChanged += delegate { SavePreferences(); };
            cookieBrowserBox.SelectedIndexChanged += delegate { SaveActiveProfileOptions(); };
            bitrateSettingsBox.SelectedIndexChanged += delegate { SaveActiveProfileOptions(); };
            foreach (AccentCheckBox option in MetadataOptionControls()) option.CheckedChanged += delegate { UpdateMetadataSelectAll(); SaveActiveProfileOptions(); };
            allMetadataFieldsToggle.CheckedChanged += delegate { ToggleAllMetadataFields(); };
            metadataToggle.CheckedChanged += delegate { UpdateMetadataControls(); SaveActiveProfileOptions(); };
            thumbnailToggle.CheckedChanged += delegate { SaveActiveProfileOptions(); };
            titleFilterToggle.CheckedChanged += delegate { UpdateTitleCleanupControls(); SaveActiveProfileOptions(); };
            removeArtistPrefixToggle.CheckedChanged += delegate { SaveActiveProfileOptions(); };
            foreach (TitleFilterOption option in titleFilterOptions) option.CheckBox.CheckedChanged += delegate { UpdateTitleTagsSelectAll(); SaveActiveProfileOptions(); };
            allTitleTagsToggle.CheckedChanged += delegate { ToggleAllTitleTags(); };
            filterRulesBox.TextChanged += delegate { SaveActiveProfileOptions(); };
            downloads.MouseClick += delegate(object sender, MouseEventArgs e) { CopySourceLinkAt(downloads, e.Location, 3); };
            failures.MouseClick += delegate(object sender, MouseEventArgs e) { CopySourceLinkAt(failures, e.Location, 2); };
            downloads.MouseMove += delegate(object sender, MouseEventArgs e) { UpdateSourceLinkCursor(downloads, e.Location, 3); };
            failures.MouseMove += delegate(object sender, MouseEventArgs e) { UpdateSourceLinkCursor(failures, e.Location, 2); };
            downloads.MouseLeave += delegate { downloads.Cursor = Cursors.Default; };
            failures.MouseLeave += delegate { failures.Cursor = Cursors.Default; };
            pasteButton.Click += delegate { PasteLinks(); };
            clearLinksButton.Click += delegate { if (!running) { urlBox.Clear(); urlBox.Focus(); } };
            openButton.Click += delegate { OpenFolder(downloadFolder); };
            downloadButton.Click += async delegate { await StartDownloadsAsync(); };
            cancelButton.Click += async delegate
            {
                if (!running) return;
                cancelButton.Enabled = false;
                progress.Value = 0;
                currentCombinedSpeed = 0.0;
                UpdateSpeedAndElapsedDisplay();
                SetStatus("Cancelling download...", Color.Orange);
                if (cancellation != null) cancellation.Cancel();
                await Task.Run((Action)KillActiveProcesses);
            };
            clearFailedButton.Click += delegate
            {
                if (running) return;
                failures.Items.Clear(); EnsureColumnsFit(failures); UpdateFailedTab();
            };
            clearActivityButton.Click += delegate
            {
                if (running) return;
                downloads.Items.Clear(); EnsureColumnsFit(downloads); SetStatus("Activity cleared.", Muted);
            };
            infoButton.Click += delegate { ShowAppInfo(); };
            updateButton.Click += async delegate { await CheckForComponentUpdatesAsync(); };
            settingsButton.Click += delegate
            {
                CenterSettingsWindow();
                if (!settingsWindow.Visible)
                    settingsWindow.Show(this);
                settingsWindow.Activate();
            };
            filterProfileBox.SelectedIndexChanged += delegate { ChangeSelectedProfile(filterProfileBox, activeProfileBox); };
            activeProfileBox.SelectedIndexChanged += delegate { ChangeSelectedProfile(activeProfileBox, filterProfileBox); };
            newProfileButton.Click += delegate { CreateProfile(); };
            saveProfileButton.Click += delegate { SaveCurrentProfile(); };
            deleteProfileButton.Click += delegate { DeleteCurrentProfile(); };
            closeSettingsButton.Click += delegate { SavePreferences(); settingsWindow.Hide(); };
            FormClosing += delegate(object sender, FormClosingEventArgs e)
            {
                SavePreferences();
                if (!running) return;
                if (!ShowConfirmation(this, "Download in progress", "Cancel the active download?",
                    "The unfinished downloads will be removed before the program closes.", "Close", Red, Purple))
                { e.Cancel = true; return; }
                if (cancellation != null) cancellation.Cancel();
                KillActiveProcesses();
            };
            FormClosed += delegate
            {
                elapsedUiTimer.Stop();
                elapsedUiTimer.Dispose();
                if (settingsWindow != null) { disposingSettings = true; settingsWindow.Dispose(); }
            };
            FormClosed += delegate { foreach (DarkHeaderWindow header in darkHeaderWindows) header.Dispose(); darkHeaderWindows.Clear(); };
        }

        private async Task StartDownloadsAsync()
        {
            if (running) return;
            List<string> inputLinks = GetLinks();
            if (inputLinks.Count == 0) { SetStatus("Paste at least one YouTube link.", Red); urlBox.Focus(); return; }
            if (!File.Exists(ytdlpPath)) { SetStatus("yt-dlp.exe is missing from the Dependencies folder.", Red); return; }
            if (!audioMode.Checked && !File.Exists(ffmpegPath)) { SetStatus("ffmpeg.exe is missing from the Dependencies folder.", Red); return; }

            Directory.CreateDirectory(downloadFolder);
            running = true;
            cancellation = new CancellationTokenSource();
            sessionFailures.Clear();
            sessionFailureReportPath = null;
            sessionFailureReportWritten = false;
            SetBusy(true);
            downloads.Items.Clear();
            progress.Value = 0;
            currentCombinedSpeed = 0.0;
            sessionElapsed.Restart();
            elapsedUiTimer.Start();
            UpdateSpeedAndElapsedDisplay();

            try
            {
                SetStatus("Reading links and playlists...", Purple);
                List<DownloadItem> queue = await BuildQueueAsync(inputLinks, cancellation.Token);
                if (queue.Count == 0)
                {
                    string reason = sessionFailures.Count > 0
                        ? sessionFailures[sessionFailures.Count - 1].Reason
                        : "Could not read the link. See Failed downloads for the exact reason.";
                    SetStatus(reason, Red);
                    resultTabs.SelectedIndex = 1;
                    return;
                }
                int rejectedBeforeDownload = sessionFailures.Count;
                progress.Maximum = 1000;
                int parallelLimit = ParseDownloadCount(simultaneousDownloads.Text);
                DownloadSessionState session = new DownloadSessionState(queue.Count);
                using (SemaphoreSlim gate = new SemaphoreSlim(parallelLimit, parallelLimit))
                {
                    List<Task> jobs = new List<Task>();
                    for (int index = 0; index < queue.Count; index++)
                        jobs.Add(RunQueuedDownloadAsync(queue[index], index, queue.Count, gate, session, cancellation.Token));
                    await Task.WhenAll(jobs.ToArray());
                }
                int totalFailed = session.Failed + rejectedBeforeDownload;
                string reportNote = totalFailed > 0 && sessionFailureReportWritten && !string.IsNullOrEmpty(sessionFailureReportPath)
                    ? " Report: " + Path.GetFileName(sessionFailureReportPath) + "." : "";
                SetStatus("Finished: " + session.Completed + " succeeded, " + totalFailed + " failed." + reportNote,
                    totalFailed == 0 ? Green : Color.Orange);
                if (totalFailed > 0) resultTabs.SelectedIndex = 1;
            }
            catch (OperationCanceledException)
            {
                progress.Value = 0;
                currentCombinedSpeed = 0.0;
                UpdateSpeedAndElapsedDisplay();
                SetStatus("Download cancelled.", Color.Orange);
            }
            catch (Exception ex)
            {
                WriteLog("DOWNLOAD SESSION ERROR: " + ex);
                SetStatus("Stopped: " + ex.Message, Red);
            }
            finally
            {
                running = false;
                lock (activeProcessesLock) activeProcesses.Clear();
                cancellation.Dispose();
                cancellation = null;
                sessionElapsed.Stop();
                elapsedUiTimer.Stop();
                currentCombinedSpeed = 0.0;
                UpdateSpeedAndElapsedDisplay();
                SetBusy(false);
            }
        }

        private async Task CheckForComponentUpdatesAsync()
        {
            if (running || !updateButton.Enabled) return;
            updateButton.Enabled = false;
            downloadButton.Enabled = false;
            WriteLog("MANUAL COMPONENT UPDATE CHECK STARTED");
            try
            {
                List<ComponentUpdateResult> results = new List<ComponentUpdateResult>();
                results.Add(await UpdateYtDlpComponentAsync());
                results.Add(await UpdateDenoComponentAsync());
                results.Add(await UpdateFfmpegComponentAsync());

                string summary = string.Join(", ", results.Select(result => result.Name + " " +
                    (!result.Success ? "failed" : (result.Updated ? "updated" : "current"))).ToArray());
                bool allSucceeded = results.All(result => result.Success);
                SetStatus(allSucceeded ? "Component Check: All Up To Date." : "Component Check: " + summary + ".",
                    allSucceeded ? Green : Color.Orange);
                WriteLog("MANUAL COMPONENT UPDATE CHECK FINISHED: " + summary);
                string failureDetails = string.Join("; ", results.Where(result => !result.Success)
                    .Select(result => result.Name + ": " + result.Detail).ToArray());
                if (failureDetails.Length > 0) WriteLog("COMPONENT UPDATE FAILURES: " + failureDetails);
            }
            catch (Exception ex)
            {
                WriteLog("MANUAL COMPONENT UPDATE ERROR: " + ex);
                if (!IsDisposed) SetStatus("Component update failed. See YTD.log for details.", Red);
            }
            finally
            {
                if (!IsDisposed)
                {
                    updateButton.Enabled = true;
                    downloadButton.Enabled = true;
                }
            }
        }

        private async Task<ComponentUpdateResult> UpdateYtDlpComponentAsync()
        {
            if (!File.Exists(ytdlpPath)) return ComponentUpdateResult.Failed("yt-dlp", "yt-dlp.exe is missing");
            SetStatus("Checking yt-dlp for updates...", Blue);
            try
            {
                ProcessResult result = await RunProcessAsync("--ignore-config --update", null, CancellationToken.None);
                if (!result.Success)
                {
                    string detail = FriendlyError(string.IsNullOrWhiteSpace(result.Error) ? result.Output : result.Error);
                    WriteLog("YT-DLP UPDATE FAILED: " + detail);
                    return ComponentUpdateResult.Failed("yt-dlp", detail);
                }

                string combined = result.Output + Environment.NewLine + result.Error;
                bool updated = combined.IndexOf("Updated yt-dlp", StringComparison.OrdinalIgnoreCase) >= 0;
                WriteLog("YT-DLP UPDATE SUCCEEDED: " + LastNonEmptyLine(combined));
                return ComponentUpdateResult.Succeeded("yt-dlp", updated);
            }
            catch (Exception ex)
            {
                WriteLog("YT-DLP UPDATE ERROR: " + ex);
                return ComponentUpdateResult.Failed("yt-dlp", ex.Message);
            }
        }

        private async Task<ComponentUpdateResult> UpdateDenoComponentAsync()
        {
            if (!File.Exists(denoPath)) return ComponentUpdateResult.Failed("Deno", "deno.exe is missing");
            SetStatus("Checking Deno for updates...", Blue);
            try
            {
                ProcessResult result = await RunToolProcessAsync(denoPath, "deno.exe", "upgrade", null, CancellationToken.None);
                if (!result.Success)
                {
                    string detail = FriendlyError(string.IsNullOrWhiteSpace(result.Error) ? result.Output : result.Error);
                    WriteLog("DENO UPDATE FAILED: " + detail);
                    return ComponentUpdateResult.Failed("Deno", detail);
                }

                string combined = result.Output + Environment.NewLine + result.Error;
                bool updated = combined.IndexOf("Upgrade done", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    combined.IndexOf("upgrading to", StringComparison.OrdinalIgnoreCase) >= 0;
                CleanupDenoUpdateArtifacts();
                WriteLog("DENO UPDATE SUCCEEDED: " + LastNonEmptyLine(combined));
                return ComponentUpdateResult.Succeeded("Deno", updated);
            }
            catch (Exception ex)
            {
                WriteLog("DENO UPDATE ERROR: " + ex);
                return ComponentUpdateResult.Failed("Deno", ex.Message);
            }
        }

        private async Task<ComponentUpdateResult> UpdateFfmpegComponentAsync()
        {
            const string versionUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.ver";
            const string checksumUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.sha256";
            const string packageUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
            string updateDirectory = Path.Combine(dependenciesDir, ".component-update-" + Guid.NewGuid().ToString("N"));
            SetStatus("Checking FFmpeg for updates...", Blue);
            try
            {
                ServicePointManager.SecurityProtocol |= (SecurityProtocolType)3072;
                Directory.CreateDirectory(updateDirectory);
                string archivePath = Path.Combine(updateDirectory, "ffmpeg-update.zip");
                string extractDirectory = Path.Combine(updateDirectory, "extracted");
                string latestVersion;
                string expectedChecksum;

                using (WebClient client = new WebClient())
                {
                    client.Headers[HttpRequestHeader.UserAgent] = "YT Downloader component updater";
                    latestVersion = (await client.DownloadStringTaskAsync(new Uri(versionUrl))).Trim();
                    expectedChecksum = FirstToken(await client.DownloadStringTaskAsync(new Uri(checksumUrl)));
                    if (string.IsNullOrWhiteSpace(latestVersion) || expectedChecksum.Length != 64)
                        throw new InvalidDataException("The FFmpeg version or checksum response was invalid.");

                    string currentVersionLine = "";
                    if (File.Exists(ffmpegPath))
                    {
                        ProcessResult current = await RunToolProcessAsync(ffmpegPath, "ffmpeg.exe", "-version", null, CancellationToken.None);
                        currentVersionLine = FirstNonEmptyLine(current.Output + Environment.NewLine + current.Error);
                    }
                    if (Regex.IsMatch(currentVersionLine, @"^ffmpeg version\s+" + Regex.Escape(latestVersion) + @"(?:-|\s|$)", RegexOptions.IgnoreCase))
                    {
                        WriteLog("FFMPEG IS CURRENT: " + currentVersionLine);
                        return ComponentUpdateResult.Succeeded("FFmpeg", false);
                    }

                    int lastProgress = -1;
                    client.DownloadProgressChanged += delegate(object sender, DownloadProgressChangedEventArgs e)
                    {
                        if (e.ProgressPercentage == lastProgress || IsDisposed || !IsHandleCreated) return;
                        lastProgress = e.ProgressPercentage;
                        BeginInvoke((Action)delegate { SetStatus("Downloading FFmpeg update... " + e.ProgressPercentage + "%", Blue); });
                    };
                    await client.DownloadFileTaskAsync(new Uri(packageUrl), archivePath);
                }

                string actualChecksum = ComputeSha256(archivePath);
                if (!string.Equals(actualChecksum, expectedChecksum, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidDataException("The FFmpeg package checksum did not match the published checksum.");

                ZipFile.ExtractToDirectory(archivePath, extractDirectory);
                string newFfmpeg = Directory.EnumerateFiles(extractDirectory, "ffmpeg.exe", SearchOption.AllDirectories).FirstOrDefault();
                string newFfprobe = Directory.EnumerateFiles(extractDirectory, "ffprobe.exe", SearchOption.AllDirectories).FirstOrDefault();
                if (newFfmpeg == null || newFfprobe == null)
                    throw new InvalidDataException("The FFmpeg package did not contain ffmpeg.exe and ffprobe.exe.");

                ProcessResult validation = await RunToolProcessAsync(newFfmpeg, "downloaded ffmpeg.exe", "-version", null, CancellationToken.None);
                string downloadedVersionLine = FirstNonEmptyLine(validation.Output + Environment.NewLine + validation.Error);
                if (!validation.Success || !Regex.IsMatch(downloadedVersionLine,
                    @"^ffmpeg version\s+" + Regex.Escape(latestVersion) + @"(?:-|\s|$)", RegexOptions.IgnoreCase))
                    throw new InvalidDataException("The downloaded FFmpeg executable did not report the expected version.");

                ReplaceFfmpegFiles(newFfmpeg, newFfprobe);
                WriteLog("FFMPEG UPDATED TO " + latestVersion + " FROM THE CHECKSUM-VERIFIED GYAN WINDOWS PACKAGE.");
                return ComponentUpdateResult.Succeeded("FFmpeg", true);
            }
            catch (Exception ex)
            {
                WriteLog("FFMPEG UPDATE ERROR: " + ex);
                return ComponentUpdateResult.Failed("FFmpeg", ex.Message);
            }
            finally { DeleteDirectoryQuietly(updateDirectory); }
        }

        private void ReplaceFfmpegFiles(string newFfmpeg, string newFfprobe)
        {
            string suffix = ".update-backup-" + Guid.NewGuid().ToString("N");
            string ffmpegBackup = ffmpegPath + suffix;
            string ffprobeBackup = ffprobePath + suffix;
            bool ffmpegHadFile = File.Exists(ffmpegPath);
            bool ffprobeHadFile = File.Exists(ffprobePath);
            try
            {
                if (ffmpegHadFile) File.Copy(ffmpegPath, ffmpegBackup, true);
                if (ffprobeHadFile) File.Copy(ffprobePath, ffprobeBackup, true);
                File.Copy(newFfmpeg, ffmpegPath, true);
                File.Copy(newFfprobe, ffprobePath, true);
            }
            catch
            {
                try
                {
                    if (ffmpegHadFile && File.Exists(ffmpegBackup)) File.Copy(ffmpegBackup, ffmpegPath, true);
                    else if (!ffmpegHadFile && File.Exists(ffmpegPath)) File.Delete(ffmpegPath);
                    if (ffprobeHadFile && File.Exists(ffprobeBackup)) File.Copy(ffprobeBackup, ffprobePath, true);
                    else if (!ffprobeHadFile && File.Exists(ffprobePath)) File.Delete(ffprobePath);
                }
                catch { }
                throw;
            }
            finally
            {
                DeleteFileQuietly(ffmpegBackup);
                DeleteFileQuietly(ffprobeBackup);
            }
        }

        private static string ComputeSha256(string path)
        {
            using (SHA256 algorithm = SHA256.Create())
            using (FileStream stream = File.OpenRead(path))
                return string.Concat(algorithm.ComputeHash(stream).Select(value => value.ToString("x2")).ToArray());
        }

        private static string FirstToken(string value)
        {
            return (value ?? "").Split((char[])null, StringSplitOptions.RemoveEmptyEntries).FirstOrDefault() ?? "";
        }

        private static string FirstNonEmptyLine(string value)
        {
            return (value ?? "").Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries)
                .Select(line => line.Trim()).FirstOrDefault(line => line.Length > 0) ?? "";
        }

        private static string LastNonEmptyLine(string value)
        {
            return (value ?? "").Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries)
                .Select(line => line.Trim()).LastOrDefault(line => line.Length > 0) ?? "";
        }

        private static void DeleteFileQuietly(string path)
        {
            try { if (File.Exists(path)) File.Delete(path); } catch { }
        }

        private void CleanupDenoUpdateArtifacts()
        {
            string directory = Path.GetDirectoryName(denoPath) ?? appDir;
            string backupPath = Path.Combine(directory,
                Path.GetFileNameWithoutExtension(denoPath) + ".old" + Path.GetExtension(denoPath));
            if (File.Exists(backupPath))
            {
                try
                {
                    File.Delete(backupPath);
                    WriteLog("REMOVED DENO UPDATE BACKUP: " + Path.GetFileName(backupPath));
                }
                catch (Exception ex)
                {
                    WriteLog("COULD NOT REMOVE DENO UPDATE BACKUP: " + ex.Message);
                }
            }

            string cachePath = Path.Combine(dependenciesDir, "DenoCache");
            if (Directory.Exists(cachePath))
            {
                try
                {
                    Directory.Delete(cachePath, true);
                    WriteLog("REMOVED DENO UPDATE CACHE");
                }
                catch (Exception ex)
                {
                    WriteLog("COULD NOT REMOVE DENO UPDATE CACHE: " + ex.Message);
                }
            }
        }

        private static void DeleteDirectoryQuietly(string path)
        {
            try { if (Directory.Exists(path)) Directory.Delete(path, true); } catch { }
        }

        private async Task<List<DownloadItem>> BuildQueueAsync(List<string> links, CancellationToken token)
        {
            List<DownloadItem> queue = new List<DownloadItem>();
            foreach (string link in links)
            {
                token.ThrowIfCancellationRequested();
                ProcessResult result = await RunProcessAsync("--ignore-config " + JavaScriptArguments() + CookieArguments() +
                    "--flat-playlist --print \"%(title)s\t%(webpage_url)s\t%(live_status|not_live)s\" " + Quote(link), null, token);
                if (!result.Success && string.IsNullOrWhiteSpace(result.Output))
                {
                    queue.Add(new DownloadItem(link, link));
                    continue;
                }
                string[] lines = result.Output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
                foreach (string line in lines)
                {
                    int statusTab = line.LastIndexOf('\t');
                    if (statusTab <= 0) continue;
                    string liveStatus = line.Substring(statusTab + 1).Trim();
                    string mediaFields = line.Substring(0, statusTab);
                    int urlTab = mediaFields.LastIndexOf('\t');
                    if (urlTab <= 0) continue;
                    string title = mediaFields.Substring(0, urlTab).Trim();
                    string url = mediaFields.Substring(urlTab + 1).Trim();
                    if (!url.StartsWith("http", StringComparison.OrdinalIgnoreCase)) continue;

                    DownloadItem item = new DownloadItem(title, url);
                    if (string.Equals(liveStatus, "is_live", StringComparison.OrdinalIgnoreCase))
                    {
                        AddFailure(item, "Active YouTube livestreams cannot be downloaded.");
                        continue;
                    }
                    if (string.Equals(liveStatus, "is_upcoming", StringComparison.OrdinalIgnoreCase))
                    {
                        AddFailure(item, "Upcoming YouTube livestreams cannot be downloaded.");
                        continue;
                    }
                    queue.Add(item);
                }
                if (lines.Length == 0 && IsWebLink(link)) queue.Add(new DownloadItem(link, link));
            }
            return queue.GroupBy(x => x.Url, StringComparer.OrdinalIgnoreCase).Select(x => x.First()).ToList();
        }

        private async Task RunQueuedDownloadAsync(DownloadItem item, int itemIndex, int totalItems,
            SemaphoreSlim gate, DownloadSessionState session, CancellationToken token)
        {
            bool entered = false;
            ListViewItem row = null;
            try
            {
                await gate.WaitAsync(token);
                entered = true;
                token.ThrowIfCancellationRequested();
                session.Started++;
                session.Active[itemIndex] = true;
                session.ItemSpeeds[itemIndex] = 0.0;
                row = AddActivity(item);
                UpdateSessionIndicators(session);
                SetStatus("Downloading " + session.Started + " of " + totalItems + " (" + session.Active.Count(value => value) + " active).", Purple);
                ProcessResult result = await DownloadOneAsync(item, row, itemIndex, session, token);
                session.ItemProgress[itemIndex] = 100.0;
                if (result.Success)
                {
                    session.Completed++;
                    SetRowStatus(row, "Finished", Green);
                }
                else
                {
                    session.Failed++;
                    SetRowStatus(row, "Failed", Red);
                    AddFailure(item, FriendlyError(result.Error));
                }
            }
            catch (OperationCanceledException) { throw; }
            catch (Exception ex)
            {
                WriteLog("DOWNLOAD ITEM ERROR: " + ex);
                session.ItemProgress[itemIndex] = 100.0;
                session.Failed++;
                if (row == null) row = AddActivity(item);
                SetRowStatus(row, "Failed", Red);
                AddFailure(item, ex.Message);
            }
            finally
            {
                if (entered)
                {
                    session.Active[itemIndex] = false;
                    session.ItemSpeeds[itemIndex] = 0.0;
                    UpdateSessionIndicators(session);
                    gate.Release();
                }
            }
        }

        private void UpdateSessionIndicators(DownloadSessionState session)
        {
            double progressTotal = session.ItemProgress.Sum();
            progress.Value = Math.Max(0, Math.Min(progress.Maximum,
                (int)Math.Round(progressTotal * progress.Maximum / (100.0 * session.ItemProgress.Length))));
            double combinedSpeed = session.ItemSpeeds.Where((value, index) => session.Active[index]).Sum();
            currentCombinedSpeed = combinedSpeed;
            UpdateSpeedAndElapsedDisplay();
        }

        private async Task<ProcessResult> DownloadOneAsync(DownloadItem item, ListViewItem row, int itemIndex,
            DownloadSessionState session, CancellationToken token)
        {
            string outputFolder = downloadFolder;
            string temporaryPrefix = ".ytd-" + Guid.NewGuid().ToString("N") + "-";
            string outputTemplate = Path.Combine(outputFolder, temporaryPrefix + "%(title)s.%(ext)s");
            AudioSourceInfo audioSource = null;
            string selectedAudioFormat = audioMode.Checked ? audioFormat.Text.ToLowerInvariant() : "";
            string ytDlpAudioFormat = YtDlpAudioFormat(selectedAudioFormat);
            FilterProfile activeProfile = CurrentProfile();
            string bitrateSetting = activeProfile == null ? "Automatic" : activeProfile.BitrateSetting;
            string audioSelector = AudioFormatSelector(selectedAudioFormat, bitrateSetting);
            if (audioMode.Checked)
            {
                SetRowStatus(row, "Checking bitrate", TextColor);
                audioSource = await ProbeAudioSourceAsync(item.Url, audioSelector, token);
                if (audioSource != null && audioSource.AverageBitrateKbps > 0.0)
                {
                    row.SubItems[1].Text = "Audio \u00B7 " + audioFormat.Text + " \u00B7 " +
                        Math.Round(audioSource.AverageBitrateKbps).ToString(System.Globalization.CultureInfo.InvariantCulture) + " kbps source";
                    EnsureColumnsFit(downloads);
                }
                SetRowStatus(row, "Starting", TextColor);
            }
            StringBuilder args = new StringBuilder();
            args.Append("--ignore-config ").Append(JavaScriptArguments()).Append(CookieArguments())
                .Append("--no-playlist --newline --windows-filenames --break-match-filters !is_live ");
            bool embedStandardMetadata = metadataToggle.Checked && MetadataOptionControls().Any(x => x.Checked && x != metadataChaptersToggle);
            if (embedStandardMetadata)
            {
                args.Append("--embed-metadata --no-embed-info-json ");
                AppendDisabledMetadataOverrides(args);
            }
            else if (!metadataToggle.Checked)
            {
                args.Append("--no-embed-metadata --no-embed-info-json ");
            }
            if (metadataToggle.Checked && metadataChaptersToggle.Checked) args.Append("--embed-chapters ");
            else args.Append("--no-embed-chapters ");
            if (titleFilterToggle.Checked)
            {
                FilterProfile profile = CurrentProfile();
                if (profile != null)
                {
                    foreach (string rule in profile.Rules.Where(x => !string.IsNullOrWhiteSpace(x)))
                        args.Append("--replace-in-metadata title ").Append(Quote(TitleRemovalPattern(rule.Trim()))).Append(" \"\" ");
                    if (profile.RemoveArtistPrefix)
                        args.Append("--replace-in-metadata title ").Append(Quote("(?i)^\\s*.+?\\s+[-–—]\\s+")).Append(" \"\" ");
                }
            }
            args.Append("--ffmpeg-location ").Append(Quote(Path.GetDirectoryName(ffmpegPath))).Append(' ');
            args.Append("--output ").Append(Quote(outputTemplate)).Append(' ');
            if (audioMode.Checked)
            {
                if (thumbnailToggle.Checked && selectedAudioFormat != "wav" && selectedAudioFormat != "aac") args.Append("--embed-thumbnail ");
                args.Append("--extract-audio --format ").Append(Quote(audioSelector)).Append(" --audio-format ").Append(ytDlpAudioFormat).Append(' ');
                int targetBitrate = AudioTranscodeBitrate(ytDlpAudioFormat, audioSource, bitrateSetting);
                if (targetBitrate > 0)
                {
                    args.Append("--audio-quality ").Append(targetBitrate).Append("K ");
                    WriteLog("AUDIO PLAN: convert " + (audioSource == null ? "unknown source" : audioSource.Codec) +
                        " to " + ytDlpAudioFormat + " at " + targetBitrate + " kbps.");
                }
                else if (ytDlpAudioFormat == "flac" || ytDlpAudioFormat == "wav")
                    WriteLog("AUDIO PLAN: decode the YouTube source to " + ytDlpAudioFormat +
                        "; this is lossless processing but cannot restore quality missing from the source.");
                else WriteLog("AUDIO PLAN: preserve the native " + ytDlpAudioFormat + " stream without lossy re-encoding.");
            }
            else
            {
                if (thumbnailToggle.Checked) args.Append("--embed-thumbnail ");
                string height = ResolutionHeight();
                string limit = string.IsNullOrEmpty(height) ? "" : "[height<=" + height + "]";
                string selector;
                if (videoFormat.Text == "MP4")
                    selector = "bestvideo" + limit + "[ext=mp4]+bestaudio[ext=m4a]/bestvideo" + limit + "+bestaudio/best" + limit;
                else if (videoFormat.Text == "WEBM")
                    selector = "bestvideo" + limit + "[ext=webm]+bestaudio[ext=webm]/bestvideo" + limit + "+bestaudio/best" + limit;
                else
                    selector = "bestvideo" + limit + "+bestaudio/best" + limit;
                args.Append("--format ").Append(Quote(selector)).Append(' ');
                args.Append("--merge-output-format ").Append(videoFormat.Text.ToLowerInvariant()).Append(' ');
            }
            args.Append(Quote(item.Url));
            int streamPhase = -1;
            int expectedStreams = audioMode.Checked ? 1 : 2;
            double highestItemProgress = 0.0;
            long lastProgressUiUpdate = 0;
            string lastProgressText = null;
            try
            {
                ProcessResult result = await RunProcessAsync(args.ToString(), delegate(string line)
                {
                    if ((line ?? "").StartsWith("[download] Destination:", StringComparison.OrdinalIgnoreCase)) streamPhase++;
                    string percent = ExtractPercent(line);
                    if (percent != null)
                    {
                        double? speedMegabytes = ExtractSpeedMegabytesValue(line);
                        double streamProgress = ExtractPercentValue(percent);
                        double itemProgress = ((Math.Max(0, streamPhase) + streamProgress / 100.0) / expectedStreams) * 100.0;
                        highestItemProgress = Math.Max(highestItemProgress, Math.Min(100.0, itemProgress));
                        long now = Stopwatch.GetTimestamp();
                        if (string.Equals(percent, lastProgressText, StringComparison.Ordinal)
                            || (streamProgress < 100.0 && lastProgressUiUpdate != 0 && now - lastProgressUiUpdate < Stopwatch.Frequency / 10)) return;
                        lastProgressText = percent;
                        lastProgressUiUpdate = now;
                        BeginInvoke((Action)delegate
                        {
                            if (!session.Active[itemIndex]) return;
                            SetRowStatus(row, percent, Purple);
                            session.ItemProgress[itemIndex] = highestItemProgress;
                            if (speedMegabytes.HasValue) session.ItemSpeeds[itemIndex] = speedMegabytes.Value;
                            UpdateSessionIndicators(session);
                        });
                    }
                }, token);
                if (!result.Success)
                {
                    CleanupPrefixedFiles(outputFolder, temporaryPrefix);
                    return result;
                }
                try
                {
                    int finalized = FinalizeUniqueOutputs(outputFolder, temporaryPrefix);
                    if (finalized == 0)
                        return new ProcessResult(false, result.Output, result.Error + Environment.NewLine + "Downloaded media output was not found after processing.");
                    return result;
                }
                catch (Exception ex)
                {
                    WriteLog("FAILED TO FINALIZE UNIQUE OUTPUT: " + ex);
                    CleanupPrefixedFiles(outputFolder, temporaryPrefix);
                    return new ProcessResult(false, result.Output, result.Error + Environment.NewLine + "Could not create the numbered output file: " + ex.Message);
                }
            }
            catch (OperationCanceledException)
            {
                CleanupPrefixedFiles(outputFolder, temporaryPrefix);
                SetRowStatus(row, "Cancelled", Color.Orange);
                throw;
            }
        }

        private async Task<AudioSourceInfo> ProbeAudioSourceAsync(string url, string selector, CancellationToken token)
        {
            string arguments = "--ignore-config " + JavaScriptArguments() + CookieArguments() +
                "--no-playlist --simulate --format " + Quote(selector) +
                " --print " + Quote("%(format_id)s\t%(acodec)s\t%(abr)s\t%(tbr)s") + " " + Quote(url);
            ProcessResult result = await RunProcessAsync(arguments, null, token);
            if (!result.Success)
            {
                WriteLog("AUDIO BITRATE CHECK FAILED: " + FriendlyError(result.Error));
                return null;
            }

            string[] lines = result.Output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            foreach (string line in lines.Reverse())
            {
                string[] parts = line.Split('\t');
                if (parts.Length < 3) continue;
                double bitrate;
                if (!TryPositiveNumber(parts[2], out bitrate) &&
                    (parts.Length < 4 || !TryPositiveNumber(parts[3], out bitrate))) continue;
                AudioSourceInfo info = new AudioSourceInfo(parts[0].Trim(), parts[1].Trim(), bitrate);
                WriteLog("AUDIO SOURCE: format " + info.FormatId + ", codec " + info.Codec + ", " +
                    info.AverageBitrateKbps.ToString("0.###", System.Globalization.CultureInfo.InvariantCulture) + " kbps");
                return info;
            }

            WriteLog("AUDIO BITRATE CHECK: The selected stream did not report a bitrate.");
            return null;
        }

        private static bool TryPositiveNumber(string value, out double number)
        {
            return double.TryParse((value ?? "").Trim(), System.Globalization.NumberStyles.Float,
                System.Globalization.CultureInfo.InvariantCulture, out number) && number > 0.0;
        }

        private static string YtDlpAudioFormat(string selectedFormat)
        {
            return string.Equals(selectedFormat, "ogg", StringComparison.OrdinalIgnoreCase) ? "vorbis" : selectedFormat;
        }

        private static string AudioFormatSelector(string selectedFormat, string bitrateSetting)
        {
            bool automatic = ParseBitrateSetting(bitrateSetting) == 0;
            if (!automatic)
            {
                if (string.Equals(selectedFormat, "m4a", StringComparison.OrdinalIgnoreCase) ||
                    string.Equals(selectedFormat, "aac", StringComparison.OrdinalIgnoreCase))
                    return "bestaudio[acodec^=opus]/bestaudio[ext=webm]/bestaudio/best";
                if (string.Equals(selectedFormat, "opus", StringComparison.OrdinalIgnoreCase))
                    return "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best";
            }
            if (string.Equals(selectedFormat, "m4a", StringComparison.OrdinalIgnoreCase) ||
                string.Equals(selectedFormat, "aac", StringComparison.OrdinalIgnoreCase))
                return "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best";
            if (string.Equals(selectedFormat, "opus", StringComparison.OrdinalIgnoreCase))
                return "bestaudio[acodec^=opus]/bestaudio[ext=webm]/bestaudio/best";
            return "bestaudio/best";
        }

        private static int AudioTranscodeBitrate(string targetFormat, AudioSourceInfo source, string bitrateSetting)
        {
            if (targetFormat == "flac" || targetFormat == "wav") return 0;
            int fixedBitrate = ParseBitrateSetting(bitrateSetting);
            if (fixedBitrate > 0) return fixedBitrate;
            if (source != null && IsNativeAudioCodec(targetFormat, source.Codec)) return 0;

            double sourceBitrate = source == null ? 0.0 : source.AverageBitrateKbps;
            if (targetFormat == "mp3")
            {
                if (sourceBitrate <= 0.0 || sourceBitrate <= 160.0) return 192;
                if (sourceBitrate <= 224.0) return 256;
                return 320;
            }

            if (sourceBitrate <= 0.0 || sourceBitrate <= 64.0) return sourceBitrate <= 0.0 ? 160 : 96;
            if (sourceBitrate <= 160.0) return 160;
            if (sourceBitrate <= 224.0) return 224;
            return 256;
        }

        private static int ParseBitrateSetting(string value)
        {
            Match match = Regex.Match(value ?? "", @"^\s*(48|64|96|128|160|192|224|256|320)\s*kbps\s*$", RegexOptions.IgnoreCase);
            if (!match.Success) return 0;
            int bitrate;
            return int.TryParse(match.Groups[1].Value, out bitrate) ? bitrate : 0;
        }

        private static bool IsNativeAudioCodec(string targetFormat, string sourceCodec)
        {
            string codec = (sourceCodec ?? "").ToLowerInvariant();
            if (targetFormat == "m4a" || targetFormat == "aac")
                return codec.Contains("aac") || codec.Contains("mp4a");
            if (targetFormat == "opus") return codec.Contains("opus");
            if (targetFormat == "vorbis") return codec.Contains("vorbis");
            if (targetFormat == "mp3") return codec.Contains("mp3");
            return false;
        }

        private int FinalizeUniqueOutputs(string outputFolder, string temporaryPrefix)
        {
            List<string> outputs = Directory.EnumerateFiles(outputFolder, "*", SearchOption.TopDirectoryOnly)
                .Where(path => Path.GetFileName(path).StartsWith(temporaryPrefix, StringComparison.OrdinalIgnoreCase))
                .ToList();
            int finalized = 0;
            foreach (string sourcePath in outputs)
            {
                string temporaryName = Path.GetFileName(sourcePath);
                string finalName = temporaryName.Substring(temporaryPrefix.Length);
                if (string.IsNullOrWhiteSpace(finalName)) continue;
                string desiredPath = Path.Combine(outputFolder, finalName);
                string finalPath = MoveToNumberedPath(sourcePath, desiredPath);
                WriteLog("OUTPUT: " + finalPath);
                finalized++;
            }
            return finalized;
        }

        private static string MoveToNumberedPath(string sourcePath, string desiredPath)
        {
            string directory = Path.GetDirectoryName(desiredPath);
            string baseName = Path.GetFileNameWithoutExtension(desiredPath);
            string extension = Path.GetExtension(desiredPath);
            for (int number = 1; number < 100000; number++)
            {
                string candidate = number == 1
                    ? desiredPath
                    : Path.Combine(directory, baseName + " (" + number + ")" + extension);
                if (File.Exists(candidate)) continue;
                try
                {
                    File.Move(sourcePath, candidate);
                    return candidate;
                }
                catch (IOException)
                {
                    if (!File.Exists(candidate)) throw;
                }
            }
            throw new IOException("Too many files already use this title.");
        }

        private static void CleanupPrefixedFiles(string outputFolder, string temporaryPrefix)
        {
            try
            {
                foreach (string path in Directory.EnumerateFiles(outputFolder, "*", SearchOption.TopDirectoryOnly)
                    .Where(path => Path.GetFileName(path).StartsWith(temporaryPrefix, StringComparison.OrdinalIgnoreCase)))
                {
                    try { File.Delete(path); } catch { }
                }
            }
            catch { }
        }

        private async Task<ProcessResult> RunProcessAsync(string arguments, Action<string> lineHandler, CancellationToken token)
        {
            return await RunToolProcessAsync(ytdlpPath, "yt-dlp.exe", arguments, lineHandler, token);
        }

        private async Task<ProcessResult> RunToolProcessAsync(string executablePath, string displayName, string arguments,
            Action<string> lineHandler, CancellationToken token)
        {
            WriteLog("COMMAND: " + displayName + " " + arguments);
            ProcessStartInfo start = new ProcessStartInfo(executablePath, arguments);
            start.WorkingDirectory = dependenciesDir;
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            start.RedirectStandardOutput = true;
            start.RedirectStandardError = true;
            start.StandardOutputEncoding = Encoding.UTF8;
            start.StandardErrorEncoding = Encoding.UTF8;
            start.EnvironmentVariables["DENO_DIR"] = Path.Combine(dependenciesDir, "DenoCache");
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            try
            {
                using (Process process = new Process())
                {
                    process.StartInfo = start;
                    process.OutputDataReceived += delegate(object sender, DataReceivedEventArgs e) { if (e.Data != null) { output.AppendLine(e.Data); if (lineHandler != null) lineHandler(e.Data); } };
                    process.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs e) { if (e.Data != null) { error.AppendLine(e.Data); if (lineHandler != null) lineHandler(e.Data); } };
                    process.Start();
                    RegisterActiveProcess(process);
                    try
                    {
                        process.BeginOutputReadLine();
                        process.BeginErrorReadLine();
                        await Task.Run(delegate { process.WaitForExit(); process.WaitForExit(); });
                        WriteLog("EXIT CODE: " + process.ExitCode + Environment.NewLine +
                            "STANDARD OUTPUT:" + Environment.NewLine + output +
                            "STANDARD ERROR:" + Environment.NewLine + error);
                        token.ThrowIfCancellationRequested();
                        return new ProcessResult(process.ExitCode == 0, output.ToString(), error.ToString());
                    }
                    finally { UnregisterActiveProcess(process); }
                }
            }
            catch (Exception ex) { WriteLog(displayName + " PROCESS ERROR: " + ex); throw; }
        }

        private ListViewItem AddActivity(DownloadItem item)
        {
            ListViewItem row = new ListViewItem(item.Title);
            row.SubItems.Add(audioMode.Checked ? "Audio · " + audioFormat.Text : "Video · " + videoFormat.Text);
            row.SubItems.Add("Starting");
            row.SubItems.Add(item.Url);
            row.UseItemStyleForSubItems = false;
            row.ForeColor = TextColor;
            foreach (ListViewItem.ListViewSubItem subItem in row.SubItems) subItem.ForeColor = TextColor;
            row.SubItems[3].ForeColor = Blue;
            downloads.Items.Insert(0, row);
            EnsureColumnsFit(downloads);
            return row;
        }

        private void AddFailure(DownloadItem item, string reason)
        {
            if (InvokeRequired) { BeginInvoke((Action)delegate { AddFailure(item, reason); }); return; }
            ListViewItem row = new ListViewItem(item.Title);
            row.SubItems.Add(reason);
            row.SubItems.Add(item.Url);
            row.ForeColor = Red;
            row.UseItemStyleForSubItems = false;
            foreach (ListViewItem.ListViewSubItem subItem in row.SubItems) subItem.ForeColor = Red;
            row.SubItems[2].ForeColor = Blue;
            failures.Items.Add(row);
            sessionFailures.Add(new FailureRecord(item.Title, reason, item.Url));
            WriteFailureReport();
            EnsureColumnsFit(failures);
            UpdateFailedTab();
        }

        private void WriteFailureReport()
        {
            if (sessionFailures.Count == 0) return;
            try
            {
                if (string.IsNullOrEmpty(sessionFailureReportPath))
                {
                    string stamp = DateTime.Now.ToString("yyyy-MM-dd_HH-mm-ss", System.Globalization.CultureInfo.InvariantCulture);
                    string basePath = Path.Combine(dependenciesDir, "FailedDL_" + stamp);
                    sessionFailureReportPath = basePath + ".txt";
                    int suffix = 2;
                    while (File.Exists(sessionFailureReportPath)) sessionFailureReportPath = basePath + "_" + suffix++ + ".txt";
                }

                List<string> lines = new List<string>
                {
                    "Failed downloads",
                    "Created: " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"),
                    ""
                };
                for (int i = 0; i < sessionFailures.Count; i++)
                {
                    FailureRecord failure = sessionFailures[i];
                    lines.Add((i + 1) + ". Title: " + SingleLine(failure.Title));
                    lines.Add("   Reason: " + SingleLine(failure.Reason));
                    lines.Add("   Source: " + SingleLine(failure.Url));
                    lines.Add("");
                }
                File.WriteAllLines(sessionFailureReportPath, lines.ToArray(), new UTF8Encoding(false));
                sessionFailureReportWritten = true;
            }
            catch (Exception ex) { sessionFailureReportWritten = false; WriteLog("FAILED TO WRITE FAILURE REPORT: " + ex.Message); }
        }

        private static string SingleLine(string value)
        {
            return (value ?? "").Replace("\r", " ").Replace("\n", " ").Trim();
        }

        private void UpdateFailedTab() { resultTabs.TabPages[1].Text = "Failed downloads (" + failures.Items.Count + ")"; }
        private void SetRowStatus(ListViewItem row, string value, Color color)
        {
            ListViewItem.ListViewSubItem statusCell = row.SubItems[2];
            if (string.Equals(statusCell.Text, value, StringComparison.Ordinal) && statusCell.ForeColor == color) return;
            statusCell.Text = value;
            statusCell.ForeColor = color;
        }

        private void UpdateMode()
        {
            audioFormat.Visible = audioMode.Checked;
            videoFormat.Visible = videoMode.Checked;
            resolution.Enabled = videoMode.Checked;
            if (IsHandleCreated) SavePreferences();
        }

        private void SetBusy(bool busy)
        {
            downloadButton.Enabled = !busy;
            cancelButton.Enabled = busy;
            pasteButton.Enabled = !busy;
            clearLinksButton.Enabled = !busy;
            audioMode.Enabled = !busy;
            videoMode.Enabled = !busy;
            audioFormat.Enabled = !busy;
            videoFormat.Enabled = !busy;
            resolution.Enabled = !busy && videoMode.Checked;
            simultaneousDownloads.Enabled = !busy;
            metadataToggle.Enabled = !busy;
            thumbnailToggle.Enabled = !busy;
            titleFilterToggle.Enabled = !busy;
            cookieBrowserBox.Enabled = !busy;
            bitrateSettingsBox.Enabled = !busy;
            clearActivityButton.Enabled = !busy;
            clearFailedButton.Enabled = !busy;
            updateButton.Enabled = !busy;
            UpdateMetadataControls();
            UpdateTitleCleanupControls();
        }

        private void PasteLinks()
        {
            if (!Clipboard.ContainsText()) { SetStatus("The clipboard does not contain text.", Color.Orange); return; }
            string text = Clipboard.GetText().Trim();
            urlBox.Text = string.IsNullOrWhiteSpace(urlBox.Text) ? text : urlBox.Text.TrimEnd() + Environment.NewLine + text;
        }

        private void CopySourceLinkAt(ListView list, Point location, int sourceIndex)
        {
            ListViewHitTestInfo hit = list.HitTest(location);
            if (hit.Item == null || hit.SubItem == null || sourceIndex >= hit.Item.SubItems.Count
                || !ReferenceEquals(hit.SubItem, hit.Item.SubItems[sourceIndex])) return;
            string link = hit.Item.SubItems[sourceIndex].Text;
            if (string.IsNullOrWhiteSpace(link)) return;
            try
            {
                Clipboard.SetText(link);
                SetStatus("Source link copied to the clipboard.", Blue);
            }
            catch { SetStatus("Could not copy the source link.", Red); }
        }

        private static void UpdateSourceLinkCursor(ListView list, Point location, int sourceIndex)
        {
            ListViewHitTestInfo hit = list.HitTest(location);
            bool overLink = hit.Item != null && hit.SubItem != null && sourceIndex < hit.Item.SubItems.Count
                && ReferenceEquals(hit.SubItem, hit.Item.SubItems[sourceIndex]);
            list.Cursor = overLink ? Cursors.Hand : Cursors.Default;
        }

        private void OpenSelectedFailure()
        {
            if (failures.SelectedItems.Count == 0) return;
            string link = failures.SelectedItems[0].SubItems[2].Text;
            try { Process.Start(link); } catch { Clipboard.SetText(link); SetStatus("Could not open the link; it was copied instead.", Color.Orange); }
        }

        private void OpenFolder(string path)
        {
            try { Directory.CreateDirectory(path); Process.Start("explorer.exe", Quote(path)); }
            catch (Exception ex) { SetStatus("Could not open folder: " + ex.Message, Red); }
        }

        private void RegisterActiveProcess(Process process)
        {
            lock (activeProcessesLock) activeProcesses.Add(process);
        }

        private void UnregisterActiveProcess(Process process)
        {
            lock (activeProcessesLock) activeProcesses.Remove(process);
        }

        private void KillActiveProcesses()
        {
            List<Process> processes;
            lock (activeProcessesLock) processes = activeProcesses.ToList();
            foreach (Process process in processes)
            {
                try
                {
                    if (process == null || process.HasExited) continue;
                    int processId = process.Id;
                    WriteLog("CANCELLING PROCESS TREE: " + processId);
                    ProcessStartInfo stopInfo = new ProcessStartInfo(Path.Combine(Environment.SystemDirectory, "taskkill.exe"),
                        "/PID " + processId + " /T /F");
                    stopInfo.UseShellExecute = false;
                    stopInfo.CreateNoWindow = true;
                    using (Process stopper = Process.Start(stopInfo))
                    {
                        if (stopper != null) stopper.WaitForExit(5000);
                    }
                    if (!process.HasExited) process.Kill();
                }
                catch (Exception ex) { WriteLog("CANCEL ERROR: " + ex); }
            }
        }

        private List<string> GetLinks()
        {
            return urlBox.Lines.Select(x => x.Trim()).Where(IsWebLink).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
        }

        private static bool IsWebLink(string value)
        {
            Uri uri;
            return Uri.TryCreate(value, UriKind.Absolute, out uri) && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps);
        }

        private string ResolutionHeight()
        {
            Match match = Regex.Match(resolution.Text, @"\d+");
            return match.Success ? match.Value : "";
        }

        private static string TitleRemovalPattern(string phrase)
        {
            return @"(?i)\s*[\(\[][^\)\]]*" + Regex.Escape(phrase) + @"[^\)\]]*[\)\]]";
        }

        private string CookieArguments()
        {
            string selected = cookieBrowserBox.Text;
            if (string.Equals(selected, "Disabled", StringComparison.OrdinalIgnoreCase)) return "";
            string browser = string.Equals(selected, "Automatic", StringComparison.OrdinalIgnoreCase)
                ? DetectDefaultBrowserForCookies()
                : selected.ToLowerInvariant();
            return string.IsNullOrEmpty(browser) ? "" : "--cookies-from-browser " + browser + " ";
        }

        private string JavaScriptArguments()
        {
            return File.Exists(denoPath) ? "--js-runtimes " + Quote("deno:" + denoPath) + " " : "";
        }

        private static string DetectDefaultBrowserForCookies()
        {
            string programId = "";
            try
            {
                using (Microsoft.Win32.RegistryKey key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(
                    @"Software\Microsoft\Windows\Shell\Associations\UrlAssociations\https\UserChoice"))
                {
                    programId = key == null ? "" : Convert.ToString(key.GetValue("ProgId"));
                }
            }
            catch { }
            string detected = MapBrowserName(programId);
            if (!string.IsNullOrEmpty(detected)) return detected;
            try
            {
                uint length = 0;
                AssocQueryString(0, 2, "https", null, null, ref length);
                StringBuilder executable = new StringBuilder((int)length);
                if (AssocQueryString(0, 2, "https", null, executable, ref length) == 0)
                {
                    string associated = MapBrowserName(Path.GetFileNameWithoutExtension(executable.ToString()));
                    if (!string.IsNullOrEmpty(associated)) return associated;
                }
            }
            catch { }
            return "firefox";
        }

        private static string MapBrowserName(string value)
        {
            value = (value ?? "").ToLowerInvariant();
            if (value.Contains("firefox")) return "firefox";
            if (value.Contains("brave")) return "brave";
            if (value.Contains("vivaldi")) return "vivaldi";
            if (value.Contains("opera")) return "opera";
            if (value.Contains("msedge") || value.Contains("edge")) return "edge";
            if (value.Contains("chromium")) return "chromium";
            if (value.Contains("chrome")) return "chrome";
            return "";
        }

        [System.Runtime.InteropServices.DllImport("shlwapi.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode)]
        private static extern uint AssocQueryString(uint flags, uint associationString, string association, string extra,
            StringBuilder output, ref uint outputLength);

        private static string ExtractPercent(string line)
        {
            Match match = Regex.Match(line ?? "", @"\[download\]\s+([\d\.]+%)");
            return match.Success ? match.Groups[1].Value : null;
        }

        private static double ExtractPercentValue(string percent)
        {
            double value;
            return double.TryParse((percent ?? "").TrimEnd('%'), System.Globalization.NumberStyles.Float,
                System.Globalization.CultureInfo.InvariantCulture, out value) ? value : 0.0;
        }

        private static double? ExtractSpeedMegabytesValue(string line)
        {
            Match match = Regex.Match(line ?? "", @"\bat\s+([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?i?B)/s", RegexOptions.IgnoreCase);
            if (!match.Success) return null;
            double value;
            if (!double.TryParse(match.Groups[1].Value, System.Globalization.NumberStyles.Float,
                System.Globalization.CultureInfo.InvariantCulture, out value)) return null;
            string unit = match.Groups[2].Value.ToUpperInvariant();
            double bytesPerUnit;
            switch (unit)
            {
                case "B": bytesPerUnit = 1.0; break;
                case "KB": bytesPerUnit = 1000.0; break;
                case "KIB": bytesPerUnit = 1024.0; break;
                case "MB": bytesPerUnit = 1000000.0; break;
                case "MIB": bytesPerUnit = 1048576.0; break;
                case "GB": bytesPerUnit = 1000000000.0; break;
                case "GIB": bytesPerUnit = 1073741824.0; break;
                case "TB": bytesPerUnit = 1000000000000.0; break;
                case "TIB": bytesPerUnit = 1099511627776.0; break;
                default: return null;
            }
            return value * bytesPerUnit / 1000000.0;
        }

        private static string FormatSpeedMegabytes(double megabytes)
        {
            string format = megabytes < 10.0 ? "0.00" : (megabytes < 100.0 ? "0.0" : "0");
            return megabytes.ToString(format, System.Globalization.CultureInfo.InvariantCulture) + " MB/s";
        }

        private void UpdateSpeedAndElapsedDisplay()
        {
            speedLabel.Text = currentCombinedSpeed > 0.0 ? FormatSpeedMegabytes(currentCombinedSpeed) : "-- MB/s";
            speedLabel.ForeColor = currentCombinedSpeed > 0.0 ? TextColor : Muted;
            TimeSpan elapsed = sessionElapsed.Elapsed;
            elapsedLabel.Text = elapsed.TotalHours >= 1.0
                ? ((int)elapsed.TotalHours).ToString(System.Globalization.CultureInfo.InvariantCulture) + ":" +
                    elapsed.Minutes.ToString("00", System.Globalization.CultureInfo.InvariantCulture) + ":" +
                    elapsed.Seconds.ToString("00", System.Globalization.CultureInfo.InvariantCulture)
                : elapsed.Minutes.ToString("00", System.Globalization.CultureInfo.InvariantCulture) + ":" +
                    elapsed.Seconds.ToString("00", System.Globalization.CultureInfo.InvariantCulture);
            elapsedLabel.ForeColor = sessionElapsed.IsRunning || elapsed > TimeSpan.Zero ? TextColor : Muted;
        }

        private static string FriendlyError(string error)
        {
            string[] lines = (error ?? "").Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            string last = lines.LastOrDefault(x => x.IndexOf("ERROR:", StringComparison.OrdinalIgnoreCase) >= 0);
            if (last == null) last = lines.LastOrDefault();
            if (string.IsNullOrWhiteSpace(last)) return "Unknown download error";
            last = Regex.Replace(last, @"^.*?ERROR:\s*", "", RegexOptions.IgnoreCase).Trim();
            return last.Length > 220 ? last.Substring(0, 217) + "..." : last;
        }

        private void UpdateToolStatus()
        {
            if (!File.Exists(ytdlpPath)) SetStatus("Missing yt-dlp.exe in the Dependencies folder.", Red);
            else if (!File.Exists(ffmpegPath)) SetStatus("Missing ffmpeg.exe. Audio conversion and video merging will not work.", Color.Orange);
            else if (!File.Exists(denoPath)) SetStatus("Missing deno.exe. YouTube challenge solving will not work.", Color.Orange);
        }

        private void WriteLog(string message)
        {
            try
            {
                lock (LogLock)
                {
                    File.AppendAllText(logPath,
                        Environment.NewLine + "[" + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.fff") + "] " + message + Environment.NewLine,
                        Encoding.UTF8);
                }
            }
            catch { }
        }

        private void SetStatus(string text, Color color) { status.Text = text; status.ForeColor = color; }
        private static string Quote(string value) { return "\"" + (value ?? "").Replace("\"", "\\\"") + "\""; }

        private Label MakeLabel(string text, Font font, Color color)
        {
            return new Label { Text = text, Font = font, ForeColor = color, BackColor = Bg, AutoSize = false };
        }

        private void ConfigureRadio(RadioButton radio, string text)
        {
            radio.Text = text; radio.ForeColor = TextColor; radio.BackColor = Bg; radio.Font = normalFont;
        }

        private void ConfigureCombo(DarkDropDown box, IEnumerable<object> items, int selected)
        {
            box.Font = normalFont;
            box.SetItems(items.Select(x => x.ToString()));
            box.SelectedIndex = selected;
        }

        private void ConfigureCheck(AccentCheckBox box, string text, bool isChecked)
        {
            box.Text = text;
            box.Checked = isChecked;
            box.ForeColor = TextColor;
            box.BackColor = Bg;
            box.Font = normalFont;
        }

        private AccentCheckBox[] MetadataOptionControls()
        {
            return new[] { metadataTitleToggle, metadataArtistToggle, metadataAlbumToggle, metadataDateToggle,
                metadataDescriptionToggle, metadataSourceToggle, metadataGenreToggle, metadataTrackToggle, metadataChaptersToggle };
        }

        private void UpdateMetadataControls()
        {
            bool enabled = metadataToggle.Checked && !running;
            foreach (AccentCheckBox option in MetadataOptionControls()) option.Enabled = enabled;
            allMetadataFieldsToggle.Enabled = enabled;
        }

        private void UpdateTitleCleanupControls()
        {
            bool editable = !running;
            bool cleanupEnabled = titleFilterToggle.Checked && editable;
            foreach (TitleFilterOption option in titleFilterOptions) option.CheckBox.Enabled = cleanupEnabled;
            allTitleTagsToggle.Enabled = cleanupEnabled;
            removeArtistPrefixToggle.Enabled = cleanupEnabled;
            filterRulesBox.ReadOnly = !editable;
            filterRulesBox.BackColor = PanelLight;
            filterRulesBox.ForeColor = editable ? TextColor : Muted;
        }

        private void ToggleAllMetadataFields()
        {
            if (updatingSelectAll) return;
            updatingSelectAll = true;
            try
            {
                bool selected = allMetadataFieldsToggle.Checked;
                foreach (AccentCheckBox option in MetadataOptionControls()) option.Checked = selected;
            }
            finally
            {
                updatingSelectAll = false;
            }
            SaveActiveProfileOptions();
        }

        private void UpdateMetadataSelectAll()
        {
            if (updatingSelectAll) return;
            updatingSelectAll = true;
            try
            {
                allMetadataFieldsToggle.Checked = MetadataOptionControls().All(option => option.Checked);
            }
            finally
            {
                updatingSelectAll = false;
            }
        }

        private void ToggleAllTitleTags()
        {
            if (updatingSelectAll) return;
            updatingSelectAll = true;
            try
            {
                bool selected = allTitleTagsToggle.Checked;
                foreach (TitleFilterOption option in titleFilterOptions) option.CheckBox.Checked = selected;
            }
            finally
            {
                updatingSelectAll = false;
            }
            SaveActiveProfileOptions();
        }

        private void UpdateTitleTagsSelectAll()
        {
            if (updatingSelectAll) return;
            updatingSelectAll = true;
            try
            {
                allTitleTagsToggle.Checked = titleFilterOptions.Count > 0 &&
                    titleFilterOptions.All(option => option.CheckBox.Checked);
            }
            finally
            {
                updatingSelectAll = false;
            }
        }

        private void UpdateFilterRulesScrollBar()
        {
            if (settingsWindow == null || filterRulesBox.Height <= 0) return;
            int totalLines = Math.Max(1, filterRulesBox.Lines.Length);
            int visibleLines = Math.Max(1, (filterRulesBox.ClientSize.Height - 4) / Math.Max(1, filterRulesBox.Font.Height));
            int maximum = Math.Max(0, totalLines - visibleLines);
            int currentLine = filterRulesBox.IsHandleCreated
                ? SendMessage(filterRulesBox.Handle, 0x00CE, IntPtr.Zero, IntPtr.Zero).ToInt32()
                : 0;
            bool needed = maximum > 0;
            int fullWidth = Math.Max(100, settingsWindow.ClientSize.Width - 36);
            filterRulesBox.Width = fullWidth - (needed ? 16 : 0);
            filterRulesScrollBar.SetBounds(filterRulesBox.Left + fullWidth - 14, filterRulesBox.Top, 14, filterRulesBox.Height);
            filterRulesScrollBar.SetMetrics(maximum, Math.Min(currentLine, maximum), visibleLines, totalLines);
            filterRulesScrollBar.Visible = needed;
            if (needed) filterRulesScrollBar.BringToFront();
            ApplyRoundedRegion(filterRulesBox, 8);
        }

        private void AppendDisabledMetadataOverrides(StringBuilder args)
        {
            if (!metadataTitleToggle.Checked) AppendEmptyMetadata(args, "title");
            if (!metadataArtistToggle.Checked) { AppendEmptyMetadata(args, "artist"); AppendEmptyMetadata(args, "composer"); }
            if (!metadataAlbumToggle.Checked) { AppendEmptyMetadata(args, "album"); AppendEmptyMetadata(args, "album_artist"); AppendEmptyMetadata(args, "show"); }
            if (!metadataDateToggle.Checked) AppendEmptyMetadata(args, "date");
            if (!metadataDescriptionToggle.Checked) { AppendEmptyMetadata(args, "description"); AppendEmptyMetadata(args, "synopsis"); }
            if (!metadataSourceToggle.Checked) { AppendEmptyMetadata(args, "purl"); AppendEmptyMetadata(args, "comment"); }
            if (!metadataGenreToggle.Checked) AppendEmptyMetadata(args, "genre");
            if (!metadataTrackToggle.Checked) { AppendEmptyMetadata(args, "track"); AppendEmptyMetadata(args, "disc"); }
        }

        private static void AppendEmptyMetadata(StringBuilder args, string field)
        {
            args.Append("--parse-metadata ").Append(Quote(":(?P<meta_" + field + ">)")).Append(' ');
        }

        private void ConfigureList(ListView list, string[] columns)
        {
            list.View = View.Details;
            list.FullRowSelect = true;
            list.HideSelection = false;
            list.BorderStyle = BorderStyle.None;
            list.BackColor = Panel;
            list.ForeColor = TextColor;
            list.Font = normalFont;
            foreach (string name in columns) list.Columns.Add(name);
            list.AllowColumnReorder = true;
            list.OwnerDraw = true;
            PropertyInfo doubleBuffered = typeof(Control).GetProperty("DoubleBuffered", BindingFlags.Instance | BindingFlags.NonPublic);
            if (doubleBuffered != null) doubleBuffered.SetValue(list, true, null);
            list.DrawColumnHeader += DrawListHeader;
            list.DrawItem += delegate { };
            list.DrawSubItem += DrawListSubItem;
            if (columns.Length == 4)
            {
                list.Columns[0].Width = 180;
                list.Columns[1].Width = 229;
                list.Columns[2].Width = 135;
                list.Columns[3].Width = 186;
            }
            else
            {
                list.Columns[0].Width = 250;
                list.Columns[1].Width = 290;
                list.Columns[2].Width = 300;
            }
            list.ColumnWidthChanged += delegate(object sender, ColumnWidthChangedEventArgs e)
            {
                if (fittingColumns || suppressPreferences || !IsHandleCreated) return;
                EnsureColumnsFit(list, e.ColumnIndex);
                SavePreferences();
            };
            list.ColumnReordered += delegate { BeginInvoke((Action)SavePreferences); };
            list.HandleCreated += delegate
            {
                KeepListDarkAndLeft(list);
                IntPtr headerHandle = SendMessage(list.Handle, 0x101F, IntPtr.Zero, IntPtr.Zero);
                if (headerHandle != IntPtr.Zero && !darkHeaderWindows.Any(x => x.TargetHandle == headerHandle))
                    darkHeaderWindows.Add(new DarkHeaderWindow(headerHandle));
            };
        }

        private void EnsureColumnsFit(ListView list)
        {
            EnsureColumnsFit(list, -1);
        }

        private void EnsureColumnsFit(ListView list, int preservedColumnIndex)
        {
            if (fittingColumns || list.Columns.Count == 0 || list.ClientSize.Width < 100) return;
            fittingColumns = true;
            try
            {
                int estimatedRowsHeight = list.Items.Count * Math.Max(18, list.Font.Height + 5) + 26;
                bool needsVerticalScroll = estimatedRowsHeight > list.ClientSize.Height;
                int available = list.ClientSize.Width - (needsVerticalScroll ? SystemInformation.VerticalScrollBarWidth : 0) - 6;
                List<ColumnHeader> ordered = list.Columns.Cast<ColumnHeader>().OrderBy(x => x.DisplayIndex).ToList();
                if (available < ordered.Count * 60) return;
                foreach (ColumnHeader column in ordered) column.Width = Math.Max(60, column.Width);
                int total = ordered.Sum(x => x.Width);
                if (total < available)
                {
                    ColumnHeader filler = ordered.LastOrDefault(x => x.Index != preservedColumnIndex) ?? ordered[ordered.Count - 1];
                    filler.Width += available - total;
                }
                else if (total > available)
                {
                    int excess = total - available;
                    IEnumerable<ColumnHeader> donors = ordered
                        .Where(x => x.Index != preservedColumnIndex)
                        .OrderByDescending(x => string.Equals(x.Text, "Source link", StringComparison.OrdinalIgnoreCase))
                        .ThenByDescending(x => x.Width);
                    foreach (ColumnHeader donor in donors)
                    {
                        int reducible = Math.Max(0, donor.Width - 60);
                        int reduction = Math.Min(excess, reducible);
                        donor.Width -= reduction;
                        excess -= reduction;
                        if (excess == 0) break;
                    }
                    if (excess > 0 && preservedColumnIndex >= 0 && preservedColumnIndex < list.Columns.Count)
                    {
                        ColumnHeader preserved = list.Columns[preservedColumnIndex];
                        int reduction = Math.Min(excess, Math.Max(0, preserved.Width - 60));
                        preserved.Width -= reduction;
                    }
                }
                list.Invalidate();
                KeepListDarkAndLeft(list);
            }
            finally { fittingColumns = false; }
        }

        private static void KeepListDarkAndLeft(ListView list)
        {
            if (!list.IsHandleCreated) return;
            SetWindowTheme(list.Handle, "DarkMode_Explorer", null);
            IntPtr header = SendMessage(list.Handle, 0x101F, IntPtr.Zero, IntPtr.Zero);
            if (header != IntPtr.Zero) SetWindowTheme(header, "DarkMode_Explorer", null);
            int horizontalPosition = GetScrollPos(list.Handle, 0);
            if (horizontalPosition != 0)
            {
                SendMessage(list.Handle, 0x1014, new IntPtr(-horizontalPosition), IntPtr.Zero);
            }
            ShowScrollBar(list.Handle, 0, false);
        }

        private TabPage MakeTab(string text) { return new TabPage(text) { BackColor = Panel, ForeColor = TextColor, Padding = new Padding(8) }; }

        private void DrawListHeader(object sender, DrawListViewColumnHeaderEventArgs e)
        {
            System.Drawing.Drawing2D.GraphicsState state = e.Graphics.Save();
            try
            {
                e.Graphics.SetClip(e.Bounds);
                using (SolidBrush background = new SolidBrush(PanelLight)) e.Graphics.FillRectangle(background, e.Bounds);
                using (SolidBrush line = new SolidBrush(Purple)) e.Graphics.FillRectangle(line, e.Bounds.Left, e.Bounds.Bottom - 2, e.Bounds.Width, 2);
                using (Pen separator = new Pen(Color.FromArgb(75, 75, 75))) e.Graphics.DrawLine(separator, e.Bounds.Right - 1, e.Bounds.Top + 4, e.Bounds.Right - 1, e.Bounds.Bottom - 4);
                Rectangle textBounds = new Rectangle(e.Bounds.Left + 12, e.Bounds.Top, Math.Max(0, e.Bounds.Width - 24), e.Bounds.Height - 2);
                TextRenderer.DrawText(e.Graphics, e.Header.Text, boldFont, textBounds, TextColor,
                    TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
            }
            finally { e.Graphics.Restore(state); }
        }

        private void DrawListSubItem(object sender, DrawListViewSubItemEventArgs e)
        {
            System.Drawing.Drawing2D.GraphicsState state = e.Graphics.Save();
            try
            {
                e.Graphics.SetClip(e.Bounds);
                bool selected = e.Item.Selected;
                Color background = selected ? Color.FromArgb(75, 38, 96) : Panel;
                using (SolidBrush brush = new SolidBrush(background)) e.Graphics.FillRectangle(brush, e.Bounds);
                using (Pen separator = new Pen(Color.FromArgb(60, 60, 60))) e.Graphics.DrawLine(separator, e.Bounds.Right - 1, e.Bounds.Top, e.Bounds.Right - 1, e.Bounds.Bottom);
                Rectangle textBounds = new Rectangle(e.Bounds.Left + 12, e.Bounds.Top, Math.Max(0, e.Bounds.Width - 24), e.Bounds.Height);
                Color cellColor = e.SubItem.ForeColor.IsEmpty ? e.Item.ForeColor : e.SubItem.ForeColor;
                TextRenderer.DrawText(e.Graphics, e.SubItem.Text, normalFont, textBounds, selected ? TextColor : cellColor,
                    TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis | TextFormatFlags.NoPrefix);
            }
            finally { e.Graphics.Restore(state); }
        }

        private void LoadPreferences()
        {
            filterProfiles.Clear();
            AddDefaultProfile();
            if (!File.Exists(settingsPath)) { RefreshProfileSelector("Default"); return; }
            try
            {
                Dictionary<string, string> values = File.ReadAllLines(settingsPath)
                    .Where(x => x.Contains("="))
                    .Select(x => new { Position = x.IndexOf('='), Line = x })
                    .ToDictionary(x => x.Line.Substring(0, x.Position), x => x.Line.Substring(x.Position + 1), StringComparer.OrdinalIgnoreCase);
                int profileCount;
                if (int.TryParse(GetValue(values, "ProfileCount"), out profileCount) && profileCount > 0)
                {
                    List<FilterProfile> loaded = new List<FilterProfile>();
                    for (int i = 0; i < Math.Min(profileCount, 50); i++)
                    {
                        string name = DecodeSetting(GetValue(values, "Profile" + i + "Name"));
                        if (!string.IsNullOrWhiteSpace(name))
                        {
                            FilterProfile profile = new FilterProfile(name, new string[0]);
                            profile.PresetRules = SplitRules(DecodeSetting(GetValue(values, "Profile" + i + "PresetRules")));
                            profile.CustomRules = SplitLinesPreserve(DecodeSetting(GetValue(values, "Profile" + i + "CustomRules")));
                            profile.RebuildRules();
                            profile.EmbedThumbnail = GetBool(values, "Profile" + i + "Thumbnail", false);
                            profile.MetadataEnabled = GetBool(values, "Profile" + i + "MetadataEnabled", false);
                            profile.CleanTitles = GetBool(values, "Profile" + i + "CleanTitles", false);
                            profile.RemoveArtistPrefix = GetBool(values, "Profile" + i + "RemoveArtistPrefix", false);
                            profile.CookieBrowser = GetValue(values, "Profile" + i + "CookieBrowser") ?? "Automatic";
                            string savedBitrate = GetValue(values, "Profile" + i + "BitrateSetting");
                            profile.BitrateSetting = string.Equals(savedBitrate, "Automatic", StringComparison.OrdinalIgnoreCase)
                                ? "Automatic" : (ParseBitrateSetting(savedBitrate) > 0 ? savedBitrate : "Automatic");
                            string metadata = DecodeSetting(GetValue(values, "Profile" + i + "Metadata"));
                            profile.MetadataFields = new HashSet<string>(SplitRules(metadata), StringComparer.OrdinalIgnoreCase);
                            loaded.Add(profile);
                        }
                    }
                    if (loaded.Count > 0) { filterProfiles.Clear(); filterProfiles.AddRange(loaded); }
                }
                RefreshProfileSelector(GetValue(values, "ActiveProfile"));
                SelectCombo(audioFormat, GetValue(values, "AudioFormat"));
                SelectCombo(videoFormat, GetValue(values, "VideoFormat"));
                SelectCombo(resolution, GetValue(values, "Resolution"));
                SelectDownloadCount(simultaneousDownloads, GetValue(values, "SimultaneousDownloads"));
                videoMode.Checked = string.Equals(GetValue(values, "Mode"), "Video", StringComparison.OrdinalIgnoreCase);
                audioMode.Checked = !videoMode.Checked;
                RestoreColumns(downloads, values, "Activity");
                RestoreColumns(failures, values, "Failures");
            }
            catch { RefreshProfileSelector("Default"); }
        }

        private void SavePreferences()
        {
            if (suppressPreferences || !IsHandleCreated || downloads.Columns.Count == 0) return;
            try
            {
                string directory = Path.GetDirectoryName(settingsPath);
                Directory.CreateDirectory(directory);
                List<string> lines = new List<string>
                {
                    "[Download defaults]",
                    "Mode=" + (videoMode.Checked ? "Video" : "Audio"),
                    "AudioFormat=" + audioFormat.Text,
                    "VideoFormat=" + videoFormat.Text,
                    "Resolution=" + resolution.Text,
                    "SimultaneousDownloads=" + simultaneousDownloads.Text,
                    "",
                    "[Profiles]",
                    "ActiveProfile=" + (CurrentProfile() == null ? "" : CurrentProfile().Name),
                    "ProfileCount=" + filterProfiles.Count
                };
                for (int i = 0; i < filterProfiles.Count; i++)
                {
                    lines.Add("");
                    lines.Add("[Profile " + i + "]");
                    lines.Add("Profile" + i + "Name=" + EncodeSetting(filterProfiles[i].Name));
                    lines.Add("Profile" + i + "PresetRules=" + EncodeSetting(string.Join("\n", filterProfiles[i].PresetRules.ToArray())));
                    lines.Add("Profile" + i + "CustomRules=" + EncodeSetting(string.Join("\n", filterProfiles[i].CustomRules.ToArray())));
                    lines.Add("Profile" + i + "Thumbnail=" + filterProfiles[i].EmbedThumbnail);
                    lines.Add("Profile" + i + "MetadataEnabled=" + filterProfiles[i].MetadataEnabled);
                    lines.Add("Profile" + i + "CleanTitles=" + filterProfiles[i].CleanTitles);
                    lines.Add("Profile" + i + "RemoveArtistPrefix=" + filterProfiles[i].RemoveArtistPrefix);
                    lines.Add("Profile" + i + "CookieBrowser=" + filterProfiles[i].CookieBrowser);
                    lines.Add("Profile" + i + "BitrateSetting=" + filterProfiles[i].BitrateSetting);
                    lines.Add("Profile" + i + "Metadata=" + EncodeSetting(string.Join("\n",
                        filterProfiles[i].MetadataFields.OrderBy(x => x, StringComparer.OrdinalIgnoreCase).ToArray())));
                }
                lines.Add("");
                lines.Add("[Activity columns]");
                AddColumnSettings(lines, downloads, "Activity");
                lines.Add("");
                lines.Add("[Failed-download columns]");
                AddColumnSettings(lines, failures, "Failures");
                File.WriteAllLines(settingsPath, lines.ToArray(), Encoding.UTF8);
            }
            catch { }
        }

        private static void AddColumnSettings(List<string> lines, ListView list, string prefix)
        {
            for (int i = 0; i < list.Columns.Count; i++)
            {
                lines.Add(prefix + "Width" + i + "=" + list.Columns[i].Width);
                lines.Add(prefix + "Order" + i + "=" + list.Columns[i].DisplayIndex);
            }
        }

        private static void RestoreColumns(ListView list, Dictionary<string, string> values, string prefix)
        {
            for (int i = 0; i < list.Columns.Count; i++)
            {
                int width;
                if (int.TryParse(GetValue(values, prefix + "Width" + i), out width)) list.Columns[i].Width = Math.Max(60, Math.Min(1000, width));
            }
            List<KeyValuePair<int, int>> orders = new List<KeyValuePair<int, int>>();
            for (int i = 0; i < list.Columns.Count; i++)
            {
                int order;
                if (int.TryParse(GetValue(values, prefix + "Order" + i), out order) && order >= 0 && order < list.Columns.Count)
                    orders.Add(new KeyValuePair<int, int>(i, order));
            }
            foreach (KeyValuePair<int, int> pair in orders.OrderBy(x => x.Value)) list.Columns[pair.Key].DisplayIndex = pair.Value;
        }

        private static void SelectCombo(DarkDropDown box, string value)
        {
            if (string.IsNullOrEmpty(value)) return;
            int index = box.FindExact(value);
            if (index >= 0) box.SelectedIndex = index;
        }

        private static void SelectDownloadCount(DarkDropDown box, string value)
        {
            SelectCombo(box, value);
        }

        private static int ParseDownloadCount(string value)
        {
            Match match = Regex.Match(value ?? "", @"^\s*([1-5])\s+at once\s*$", RegexOptions.IgnoreCase);
            int count;
            return match.Success && int.TryParse(match.Groups[1].Value, out count) ? count : 2;
        }

        private static bool GetBool(Dictionary<string, string> values, string key, bool fallback)
        {
            bool result;
            return bool.TryParse(GetValue(values, key), out result) ? result : fallback;
        }

        private static string GetValue(Dictionary<string, string> values, string key)
        {
            string value;
            return values.TryGetValue(key, out value) ? value : null;
        }

        private void AddDefaultProfile()
        {
            FilterProfile profile = new FilterProfile("Default", new string[0]);
            ConfigureDefaultProfile(profile);
            filterProfiles.Add(profile);
        }

        private static void ConfigureDefaultProfile(FilterProfile profile)
        {
            profile.Rules.Clear();
            profile.PresetRules.Clear();
            profile.CustomRules.Clear();
            profile.EmbedThumbnail = false;
            profile.MetadataEnabled = false;
            profile.CleanTitles = false;
            profile.RemoveArtistPrefix = false;
            profile.CookieBrowser = "Automatic";
            profile.BitrateSetting = "Automatic";
            profile.MetadataFields.Clear();
        }

        private void RefreshProfileSelector(string preferred)
        {
            string[] profileNames = filterProfiles.Select(x => x.Name).ToArray();
            int index = filterProfiles.FindIndex(x => string.Equals(x.Name, preferred, StringComparison.OrdinalIgnoreCase));
            index = index >= 0 ? index : 0;
            syncingProfileSelectors = true;
            try
            {
                filterProfileBox.SetItems(profileNames);
                activeProfileBox.SetItems(profileNames);
                filterProfileBox.SelectedIndex = index;
                activeProfileBox.SelectedIndex = index;
            }
            finally { syncingProfileSelectors = false; }
            ShowSelectedProfile();
        }

        private void ChangeSelectedProfile(DarkDropDown source, DarkDropDown target)
        {
            if (syncingProfileSelectors) return;
            syncingProfileSelectors = true;
            try { target.SelectedIndex = source.SelectedIndex; }
            finally { syncingProfileSelectors = false; }
            ShowSelectedProfile();
            SavePreferences();
        }

        private FilterProfile CurrentProfile()
        {
            int index = filterProfileBox.SelectedIndex;
            return index >= 0 && index < filterProfiles.Count ? filterProfiles[index] : null;
        }

        private void ShowSelectedProfile()
        {
            FilterProfile profile = CurrentProfile();
            if (profile == null) { UpdateProfileIndicator(); return; }
            loadingProfileUi = true;
            try
            {
                profileNameBox.Text = profile.Name;
                thumbnailToggle.Checked = profile.EmbedThumbnail;
                metadataToggle.Checked = profile.MetadataEnabled;
                titleFilterToggle.Checked = profile.CleanTitles;
                removeArtistPrefixToggle.Checked = profile.RemoveArtistPrefix;
                SelectCombo(cookieBrowserBox, profile.CookieBrowser);
                SelectCombo(bitrateSettingsBox, profile.BitrateSetting);
                SetMetadataChecks(profile.MetadataFields);
                UpdateMetadataControls();
                UpdateTitleCleanupControls();
                HashSet<string> selected = new HashSet<string>(profile.PresetRules, StringComparer.OrdinalIgnoreCase);
                foreach (TitleFilterOption option in titleFilterOptions)
                {
                    option.CheckBox.Checked = option.Terms.All(x => selected.Contains(x));
                }
                filterRulesBox.Lines = profile.CustomRules.ToArray();
                UpdateMetadataSelectAll();
                UpdateTitleTagsSelectAll();
            }
            finally { loadingProfileUi = false; }
            UpdateProfileIndicator();
        }

        private void UpdateProfileIndicator()
        {
            int index = filterProfileBox.SelectedIndex;
            if (activeProfileBox.SelectedIndex != index)
            {
                syncingProfileSelectors = true;
                try { activeProfileBox.SelectedIndex = index; }
                finally { syncingProfileSelectors = false; }
            }
            LayoutProfileIndicator();
        }

        private void LayoutProfileIndicator()
        {
            if (activeProfileLabel == null || settingsButton == null) return;
            const int gap = 2;
            int prefixWidth = TextRenderer.MeasureText(activeProfileLabel.Text, activeProfileLabel.Font).Width;
            int right = settingsButton.Left - 10;
            int available = Math.Max(0, right - updateButton.Right - 10);
            prefixWidth = Math.Min(prefixWidth, available);
            int selectorWidth = Math.Min(175, Math.Max(0, available - prefixWidth - gap));
            int left = right - prefixWidth - gap - selectorWidth;
            activeProfileLabel.SetBounds(left, 16, prefixWidth, 28);
            activeProfileBox.SetBounds(left + prefixWidth + gap, 16, selectorWidth, 28);
            activeProfileLabel.BringToFront();
            activeProfileBox.BringToFront();
        }

        private void CreateProfile()
        {
            int number = 1;
            string name;
            do { name = "New profile " + number++; } while (filterProfiles.Any(x => string.Equals(x.Name, name, StringComparison.OrdinalIgnoreCase)));
            FilterProfile profile = new FilterProfile(name, new string[0]);
            ConfigureDefaultProfile(profile);
            filterProfiles.Add(profile);
            RefreshProfileSelector(name);
            profileNameBox.Focus();
            profileNameBox.SelectAll();
            SavePreferences();
        }

        private void SaveCurrentProfile()
        {
            FilterProfile profile = CurrentProfile();
            if (profile == null) return;
            string name = profileNameBox.Text.Replace("\r", " ").Replace("\n", " ").Trim();
            if (string.IsNullOrWhiteSpace(name)) { SetStatus("A filter profile needs a name.", Red); return; }
            if (filterProfiles.Any(x => x != profile && string.Equals(x.Name, name, StringComparison.OrdinalIgnoreCase)))
            { SetStatus("A filter profile with that name already exists.", Red); return; }
            profile.Name = name;
            CaptureProfileOptions(profile);
            RefreshProfileSelector(name);
            SavePreferences();
            SetStatus("Filter profile saved.", Green);
        }

        private void SaveActiveProfileOptions()
        {
            if (loadingProfileUi || suppressPreferences || updatingSelectAll) return;
            FilterProfile profile = CurrentProfile();
            if (profile == null) return;
            CaptureProfileOptions(profile);
            SavePreferences();
        }

        private void CaptureProfileOptions(FilterProfile profile)
        {
            profile.PresetRules = titleFilterOptions.Where(option => option.CheckBox.Checked)
                .SelectMany(option => option.Terms).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
            profile.CustomRules = filterRulesBox.Lines.ToList();
            profile.RebuildRules();
            profile.EmbedThumbnail = thumbnailToggle.Checked;
            profile.MetadataEnabled = metadataToggle.Checked;
            profile.CleanTitles = titleFilterToggle.Checked;
            profile.RemoveArtistPrefix = removeArtistPrefixToggle.Checked;
            profile.CookieBrowser = cookieBrowserBox.Text;
            profile.BitrateSetting = string.IsNullOrWhiteSpace(bitrateSettingsBox.Text) ? "Automatic" : bitrateSettingsBox.Text;
            profile.MetadataFields = SelectedMetadataFields();
        }

        private HashSet<string> SelectedMetadataFields()
        {
            HashSet<string> fields = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            if (metadataTitleToggle.Checked) fields.Add("Title");
            if (metadataArtistToggle.Checked) fields.Add("Artist");
            if (metadataAlbumToggle.Checked) fields.Add("Album");
            if (metadataDateToggle.Checked) fields.Add("Date");
            if (metadataDescriptionToggle.Checked) fields.Add("Description");
            if (metadataSourceToggle.Checked) fields.Add("Source");
            if (metadataGenreToggle.Checked) fields.Add("Genre");
            if (metadataTrackToggle.Checked) fields.Add("Track");
            if (metadataChaptersToggle.Checked) fields.Add("Chapters");
            return fields;
        }

        private void SetMetadataChecks(HashSet<string> fields)
        {
            metadataTitleToggle.Checked = fields.Contains("Title");
            metadataArtistToggle.Checked = fields.Contains("Artist");
            metadataAlbumToggle.Checked = fields.Contains("Album");
            metadataDateToggle.Checked = fields.Contains("Date");
            metadataDescriptionToggle.Checked = fields.Contains("Description");
            metadataSourceToggle.Checked = fields.Contains("Source");
            metadataGenreToggle.Checked = fields.Contains("Genre");
            metadataTrackToggle.Checked = fields.Contains("Track");
            metadataChaptersToggle.Checked = fields.Contains("Chapters");
        }

        private void DeleteCurrentProfile()
        {
            if (filterProfiles.Count <= 1) { SetStatus("At least one filter profile must remain.", Color.Orange); return; }
            FilterProfile profile = CurrentProfile();
            if (profile == null) return;
            if (!ConfirmProfileDeletion(profile)) return;
            filterProfiles.Remove(profile);
            RefreshProfileSelector(filterProfiles[0].Name);
            SavePreferences();
            SetStatus("Filter profile deleted.", Muted);
        }

        private bool ConfirmProfileDeletion(FilterProfile profile)
        {
            return ShowConfirmation(settingsWindow, "Delete profile", "Delete this profile?",
                "Profile: " + profile.Name + "\r\nThis action cannot be undone.", "Delete", Red, Purple);
        }

        private bool ShowConfirmation(IWin32Window owner, string title, string headingText, string messageText,
            string confirmText, Color confirmColor, Color borderColor)
        {
            bool confirmed = false;
            using (DarkForm dialog = new DarkForm())
            {
                dialog.Text = title;
                dialog.ClientSize = new Size(430, 178);
                dialog.FormBorderStyle = FormBorderStyle.FixedDialog;
                dialog.MaximizeBox = false;
                dialog.MinimizeBox = false;
                dialog.ShowInTaskbar = false;
                dialog.StartPosition = FormStartPosition.CenterParent;
                dialog.BackColor = Bg;
                dialog.ForeColor = TextColor;
                dialog.ChromeBorderColor = borderColor;
                dialog.Font = normalFont;
                dialog.Icon = Icon;
                dialog.KeyPreview = true;

                Label heading = MakeLabel(headingText, titleFont, TextColor);
                heading.SetBounds(20, 16, 390, 30);
                Label message = MakeLabel(messageText, normalFont, Muted);
                message.SetBounds(20, 55, 390, 48);
                message.AutoEllipsis = true;

                StyledButton cancel = new StyledButton("Cancel", Gray, boldFont);
                StyledButton confirm = new StyledButton(confirmText, confirmColor, boldFont);
                cancel.SetBounds(220, 124, 90, 32);
                confirm.SetBounds(320, 124, 90, 32);
                cancel.Click += delegate { dialog.Close(); };
                confirm.Click += delegate { confirmed = true; dialog.Close(); };
                dialog.KeyDown += delegate(object sender, KeyEventArgs e)
                {
                    if (e.KeyCode == Keys.Escape) { e.SuppressKeyPress = true; dialog.Close(); }
                };
                dialog.Shown += delegate { cancel.Focus(); };
                dialog.Controls.AddRange(new Control[] { heading, message, cancel, confirm });
                dialog.ShowDialog(owner);
            }
            return confirmed;
        }

        private static List<string> SplitRules(string value)
        {
            return (value ?? "").Split(new[] { "\r\n", "\n" }, StringSplitOptions.RemoveEmptyEntries).ToList();
        }

        private static List<string> SplitLinesPreserve(string value)
        {
            return (value ?? "").Split(new[] { "\r\n", "\n" }, StringSplitOptions.None).ToList();
        }

        private static string EncodeSetting(string value)
        {
            return Convert.ToBase64String(Encoding.UTF8.GetBytes(value ?? ""));
        }

        private static string DecodeSetting(string value)
        {
            try { return Encoding.UTF8.GetString(Convert.FromBase64String(value ?? "")); }
            catch { return ""; }
        }

        private static void ApplyRoundedRegion(Control control, int radius)
        {
            if (control.Width <= 1 || control.Height <= 1) return;
            using (GraphicsPath path = CreateRoundedPath(new Rectangle(0, 0, control.Width, control.Height), radius))
            {
                Region old = control.Region;
                control.Region = new Region(path);
                if (old != null) old.Dispose();
            }
        }

        private static void DrawRoundedOutline(Graphics graphics, Rectangle bounds, int radius, Color color)
        {
            Rectangle outline = new Rectangle(bounds.Left - 1, bounds.Top - 1, bounds.Width + 1, bounds.Height + 1);
            using (GraphicsPath path = CreateRoundedPath(outline, radius))
            using (Pen pen = new Pen(color, 1f)) graphics.DrawPath(pen, path);
        }

        private static GraphicsPath CreateRoundedPath(Rectangle bounds, int radius)
        {
            GraphicsPath path = new GraphicsPath();
            int diameter = Math.Max(2, radius * 2);
            diameter = Math.Min(diameter, Math.Min(bounds.Width, bounds.Height));
            Rectangle arc = new Rectangle(bounds.Left, bounds.Top, diameter, diameter);
            path.AddArc(arc, 180, 90);
            arc.X = bounds.Right - diameter;
            path.AddArc(arc, 270, 90);
            arc.Y = bounds.Bottom - diameter;
            path.AddArc(arc, 0, 90);
            arc.X = bounds.Left;
            path.AddArc(arc, 90, 90);
            path.CloseFigure();
            return path;
        }

        [System.Runtime.InteropServices.DllImport("uxtheme.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode)]
        private static extern int SetWindowTheme(IntPtr hwnd, string subAppName, string subIdList);

        [System.Runtime.InteropServices.DllImport("user32.dll")]
        private static extern IntPtr SendMessage(IntPtr hwnd, int message, IntPtr wParam, IntPtr lParam);

        [System.Runtime.InteropServices.DllImport("user32.dll")]
        private static extern int GetScrollPos(IntPtr hwnd, int bar);

        [System.Runtime.InteropServices.DllImport("user32.dll")]
        private static extern bool ShowScrollBar(IntPtr hwnd, int bar, bool show);

        private sealed class DownloadItem
        {
            public readonly string Title;
            public readonly string Url;
            public DownloadItem(string title, string url) { Title = title; Url = url; }
        }

        private sealed class ProcessResult
        {
            public readonly bool Success;
            public readonly string Output;
            public readonly string Error;
            public ProcessResult(bool success, string output, string error) { Success = success; Output = output; Error = error; }
        }

        private sealed class ComponentUpdateResult
        {
            public readonly string Name;
            public readonly bool Success;
            public readonly bool Updated;
            public readonly string Detail;

            private ComponentUpdateResult(string name, bool success, bool updated, string detail)
            {
                Name = name;
                Success = success;
                Updated = updated;
                Detail = detail;
            }

            public static ComponentUpdateResult Succeeded(string name, bool updated)
            {
                return new ComponentUpdateResult(name, true, updated, "");
            }

            public static ComponentUpdateResult Failed(string name, string detail)
            {
                return new ComponentUpdateResult(name, false, false, detail ?? "");
            }
        }

        private sealed class AudioSourceInfo
        {
            public readonly string FormatId;
            public readonly string Codec;
            public readonly double AverageBitrateKbps;
            public AudioSourceInfo(string formatId, string codec, double averageBitrateKbps)
            {
                FormatId = formatId;
                Codec = codec;
                AverageBitrateKbps = averageBitrateKbps;
            }
        }

        private sealed class DownloadSessionState
        {
            public int Started;
            public int Completed;
            public int Failed;
            public readonly double[] ItemProgress;
            public readonly double[] ItemSpeeds;
            public readonly bool[] Active;
            public DownloadSessionState(int count)
            {
                ItemProgress = new double[count];
                ItemSpeeds = new double[count];
                Active = new bool[count];
            }
        }

        private sealed class FilterProfile
        {
            public string Name;
            public List<string> Rules;
            public List<string> PresetRules = new List<string>();
            public List<string> CustomRules = new List<string>();
            public bool EmbedThumbnail = true;
            public bool MetadataEnabled = true;
            public bool CleanTitles = true;
            public bool RemoveArtistPrefix;
            public string CookieBrowser = "Automatic";
            public string BitrateSetting = "Automatic";
            public HashSet<string> MetadataFields = new HashSet<string>(new[]
                { "Title", "Artist", "Album", "Date", "Description", "Source", "Genre", "Track", "Chapters" }, StringComparer.OrdinalIgnoreCase);
            public FilterProfile(string name, IEnumerable<string> rules)
            {
                Name = name;
                Rules = rules.ToList();
                CustomRules = Rules.ToList();
            }

            public void RebuildRules()
            {
                Rules = PresetRules.Concat(CustomRules)
                    .Select(rule => (rule ?? "").Trim())
                    .Where(rule => rule.Length > 0)
                    .Distinct(StringComparer.OrdinalIgnoreCase)
                    .ToList();
            }
        }

        private sealed class FailureRecord
        {
            public readonly string Title;
            public readonly string Reason;
            public readonly string Url;
            public FailureRecord(string title, string reason, string url) { Title = title; Reason = reason; Url = url; }
        }

        private sealed class TitleFilterOption
        {
            public readonly AccentCheckBox CheckBox;
            public readonly string[] Terms;
            public TitleFilterOption(AccentCheckBox checkBox, string[] terms) { CheckBox = checkBox; Terms = terms; }
        }
    }

    internal sealed class DarkRulesTextBox : RichTextBox
    {
        private const int EmLineScroll = 0x00B6;
        public event EventHandler ScrollPositionChanged;

        protected override void OnMouseWheel(MouseEventArgs e)
        {
            if (IsHandleCreated && e.Delta != 0)
                SendMessage(Handle, EmLineScroll, IntPtr.Zero, new IntPtr(e.Delta > 0 ? -3 : 3));
            if (ScrollPositionChanged != null) ScrollPositionChanged(this, EventArgs.Empty);
        }

        [System.Runtime.InteropServices.DllImport("user32.dll", CharSet = System.Runtime.InteropServices.CharSet.Auto)]
        private static extern IntPtr SendMessage(IntPtr handle, int message, IntPtr wParam, IntPtr lParam);
    }

    internal sealed class DarkVerticalScrollBar : Control
    {
        private static readonly Color TrackColor = Color.FromArgb(44, 44, 44);
        private static readonly Color ThumbColor = Color.FromArgb(92, 92, 92);
        private static readonly Color ThumbHoverColor = Color.FromArgb(118, 78, 142);
        private static readonly Color Accent = Color.FromArgb(159, 0, 255);
        private int maximum;
        private int currentValue;
        private int visibleLines = 1;
        private int totalLines = 1;
        private bool dragging;
        private bool hovering;
        private int dragOffset;
        public event EventHandler ValueChanged;

        public int Value { get { return currentValue; } }

        public DarkVerticalScrollBar()
        {
            BackColor = TrackColor;
            Cursor = Cursors.Hand;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.UserPaint | ControlStyles.ResizeRedraw, true);
        }

        public void SetMetrics(int newMaximum, int newValue, int viewportLines, int contentLines)
        {
            maximum = Math.Max(0, newMaximum);
            currentValue = Math.Max(0, Math.Min(maximum, newValue));
            visibleLines = Math.Max(1, viewportLines);
            totalLines = Math.Max(visibleLines, contentLines);
            Invalidate();
        }

        protected override void OnMouseEnter(EventArgs e) { hovering = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { hovering = false; if (!dragging) Invalidate(); base.OnMouseLeave(e); }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button != MouseButtons.Left) return;
            Rectangle thumb = ThumbBounds();
            if (thumb.Contains(e.Location))
            {
                dragging = true;
                dragOffset = e.Y - thumb.Top;
                Capture = true;
            }
            else
            {
                SetValue(currentValue + (e.Y < thumb.Top ? -visibleLines : visibleLines), true);
            }
            Invalidate();
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            if (!dragging) return;
            Rectangle track = TrackBounds();
            Rectangle thumb = ThumbBounds();
            int travel = Math.Max(1, track.Height - thumb.Height);
            int top = Math.Max(track.Top, Math.Min(track.Bottom - thumb.Height, e.Y - dragOffset));
            int value = (int)Math.Round((top - track.Top) * maximum / (double)travel);
            SetValue(value, true);
        }

        protected override void OnMouseUp(MouseEventArgs e)
        {
            base.OnMouseUp(e);
            if (e.Button != MouseButtons.Left) return;
            dragging = false;
            Capture = false;
            Invalidate();
        }

        protected override void OnMouseWheel(MouseEventArgs e)
        {
            SetValue(currentValue - Math.Sign(e.Delta) * 3, true);
            base.OnMouseWheel(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            e.Graphics.Clear(TrackColor);
            Rectangle track = TrackBounds();
            using (GraphicsPath path = RoundedPath(track, 4))
            using (SolidBrush brush = new SolidBrush(Color.FromArgb(35, 35, 35))) e.Graphics.FillPath(brush, path);
            Rectangle thumb = ThumbBounds();
            using (GraphicsPath path = RoundedPath(thumb, 4))
            using (SolidBrush brush = new SolidBrush(dragging ? Accent : (hovering ? ThumbHoverColor : ThumbColor)))
                e.Graphics.FillPath(brush, path);
        }

        private void SetValue(int value, bool notify)
        {
            int clamped = Math.Max(0, Math.Min(maximum, value));
            if (clamped == currentValue) return;
            currentValue = clamped;
            Invalidate();
            if (notify && ValueChanged != null) ValueChanged(this, EventArgs.Empty);
        }

        private Rectangle TrackBounds()
        {
            return new Rectangle(3, 2, Math.Max(4, Width - 6), Math.Max(8, Height - 4));
        }

        private Rectangle ThumbBounds()
        {
            Rectangle track = TrackBounds();
            int thumbHeight = Math.Max(24, (int)Math.Round(track.Height * visibleLines / (double)Math.Max(1, totalLines)));
            thumbHeight = Math.Min(track.Height, thumbHeight);
            int travel = Math.Max(0, track.Height - thumbHeight);
            int top = maximum == 0 ? track.Top : track.Top + (int)Math.Round(travel * currentValue / (double)maximum);
            return new Rectangle(track.Left, top, track.Width, thumbHeight);
        }

        private static GraphicsPath RoundedPath(Rectangle bounds, int radius)
        {
            GraphicsPath path = new GraphicsPath();
            int diameter = Math.Min(Math.Min(bounds.Width, bounds.Height), radius * 2);
            if (diameter <= 1) { path.AddRectangle(bounds); return path; }
            path.AddArc(bounds.Left, bounds.Top, diameter, diameter, 180, 90);
            path.AddArc(bounds.Right - diameter, bounds.Top, diameter, diameter, 270, 90);
            path.AddArc(bounds.Right - diameter, bounds.Bottom - diameter, diameter, diameter, 0, 90);
            path.AddArc(bounds.Left, bounds.Bottom - diameter, diameter, diameter, 90, 90);
            path.CloseFigure();
            return path;
        }
    }

    internal sealed class DarkHeaderWindow : NativeWindow, IDisposable
    {
        private const int WmPaint = 0x000F;
        private const int HdmGetItemCount = 0x1200;
        private const int HdmGetItemRect = 0x1207;
        public IntPtr TargetHandle { get; private set; }

        public DarkHeaderWindow(IntPtr handle)
        {
            TargetHandle = handle;
            AssignHandle(handle);
        }

        protected override void WndProc(ref Message message)
        {
            base.WndProc(ref message);
            if (message.Msg == WmPaint) PaintUnusedHeader();
        }

        private void PaintUnusedHeader()
        {
            if (TargetHandle == IntPtr.Zero) return;
            NativeRect client;
            if (!GetClientRect(TargetHandle, out client)) return;
            int right = 0;
            int count = SendMessage(TargetHandle, HdmGetItemCount, IntPtr.Zero, IntPtr.Zero).ToInt32();
            IntPtr memory = System.Runtime.InteropServices.Marshal.AllocHGlobal(System.Runtime.InteropServices.Marshal.SizeOf(typeof(NativeRect)));
            try
            {
                for (int index = 0; index < count; index++)
                {
                    if (SendMessage(TargetHandle, HdmGetItemRect, new IntPtr(index), memory) != IntPtr.Zero)
                    {
                        NativeRect item = (NativeRect)System.Runtime.InteropServices.Marshal.PtrToStructure(memory, typeof(NativeRect));
                        right = Math.Max(right, item.Right);
                    }
                }
            }
            finally { System.Runtime.InteropServices.Marshal.FreeHGlobal(memory); }
            if (right >= client.Right) return;
            using (Graphics graphics = Graphics.FromHwnd(TargetHandle))
            using (SolidBrush background = new SolidBrush(Color.FromArgb(44, 44, 44)))
            using (SolidBrush accent = new SolidBrush(Color.FromArgb(159, 0, 255)))
            {
                graphics.FillRectangle(background, right, 0, client.Right - right, client.Bottom);
                graphics.FillRectangle(accent, right, Math.Max(0, client.Bottom - 2), client.Right - right, 2);
            }
        }

        public void Dispose()
        {
            try { if (Handle != IntPtr.Zero) ReleaseHandle(); } catch { }
            TargetHandle = IntPtr.Zero;
        }

        [System.Runtime.InteropServices.StructLayout(System.Runtime.InteropServices.LayoutKind.Sequential)]
        private struct NativeRect { public int Left, Top, Right, Bottom; }

        [System.Runtime.InteropServices.DllImport("user32.dll")]
        private static extern bool GetClientRect(IntPtr hwnd, out NativeRect rect);

        [System.Runtime.InteropServices.DllImport("user32.dll")]
        private static extern IntPtr SendMessage(IntPtr hwnd, int message, IntPtr wParam, IntPtr lParam);
    }

    internal sealed class DarkProgressBar : Control
    {
        private int maximum = 100;
        private int currentValue;

        public int Maximum
        {
            get { return maximum; }
            set { maximum = Math.Max(1, value); currentValue = Math.Min(currentValue, maximum); Invalidate(); }
        }

        public int Value
        {
            get { return currentValue; }
            set { currentValue = Math.Max(0, Math.Min(maximum, value)); Invalidate(); }
        }

        public DarkProgressBar()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint | ControlStyles.ResizeRedraw, true);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            using (SolidBrush background = new SolidBrush(BackColor)) e.Graphics.FillRectangle(background, ClientRectangle);
            int fillWidth = (int)Math.Round(Width * (currentValue / (double)maximum));
            if (fillWidth <= 0) return;
            using (GraphicsPath clip = RoundedPath(new Rectangle(0, 0, Width, Height), 5))
            using (SolidBrush fill = new SolidBrush(ForeColor))
            {
                e.Graphics.SetClip(clip);
                e.Graphics.FillRectangle(fill, 0, 0, fillWidth, Height);
            }
        }

        private static GraphicsPath RoundedPath(Rectangle bounds, int radius)
        {
            GraphicsPath path = new GraphicsPath(); int diameter = radius * 2;
            path.AddArc(bounds.Left, bounds.Top, diameter, diameter, 180, 90);
            path.AddArc(bounds.Right - diameter, bounds.Top, diameter, diameter, 270, 90);
            path.AddArc(bounds.Right - diameter, bounds.Bottom - diameter, diameter, diameter, 0, 90);
            path.AddArc(bounds.Left, bounds.Bottom - diameter, diameter, diameter, 90, 90);
            path.CloseFigure(); return path;
        }
    }


    internal sealed class AccentCheckBox : Control
    {
        private static readonly Color Accent = Color.FromArgb(159, 0, 255);
        private static readonly Color Surface = Color.FromArgb(44, 44, 44);
        private bool isChecked;
        public event EventHandler CheckedChanged;

        public bool Checked
        {
            get { return isChecked; }
            set
            {
                if (isChecked == value) return;
                isChecked = value;
                Invalidate();
                EventHandler handler = CheckedChanged;
                if (handler != null) handler(this, EventArgs.Empty);
            }
        }

        public AccentCheckBox()
        {
            Cursor = Cursors.Hand;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint | ControlStyles.ResizeRedraw, true);
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (Enabled && e.Button == MouseButtons.Left) Checked = !Checked;
        }

        protected override void OnKeyDown(KeyEventArgs e)
        {
            base.OnKeyDown(e);
            if (Enabled && e.KeyCode == Keys.Space) { Checked = !Checked; e.Handled = true; }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            Rectangle box = new Rectangle(1, (Height - 16) / 2, 16, 16);
            using (GraphicsPath path = RoundedBox(box, 4))
            using (SolidBrush brush = new SolidBrush(Checked ? Accent : Surface)) e.Graphics.FillPath(brush, path);
            if (Checked)
            {
                using (Pen pen = new Pen(Color.White, 2f))
                {
                    pen.StartCap = LineCap.Round; pen.EndCap = LineCap.Round;
                    e.Graphics.DrawLines(pen, new[] { new Point(box.Left + 4, box.Top + 8), new Point(box.Left + 7, box.Top + 11), new Point(box.Left + 13, box.Top + 5) });
                }
            }
            Rectangle textBounds = new Rectangle(23, 0, Math.Max(0, Width - 23), Height);
            TextRenderer.DrawText(e.Graphics, Text, Font, textBounds, Enabled ? ForeColor : Color.Gray,
                TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis | TextFormatFlags.NoPrefix);
        }

        private static GraphicsPath RoundedBox(Rectangle bounds, int radius)
        {
            GraphicsPath path = new GraphicsPath(); int d = radius * 2;
            path.AddArc(bounds.Left, bounds.Top, d, d, 180, 90); path.AddArc(bounds.Right-d, bounds.Top, d, d, 270, 90);
            path.AddArc(bounds.Right-d, bounds.Bottom-d, d, d, 0, 90); path.AddArc(bounds.Left, bounds.Bottom-d, d, d, 90, 90); path.CloseFigure();
            return path;
        }
    }

    internal sealed class DarkDropDown : Control
    {
        private static readonly Color Surface = Color.FromArgb(44, 44, 44);
        private static readonly Color Hover = Color.FromArgb(75, 38, 96);
        private static readonly Color Accent = Color.FromArgb(159, 0, 255);
        private static readonly Color LightText = Color.FromArgb(245, 245, 245);
        private readonly List<string> entries = new List<string>();
        private int selectedIndex = -1;
        private ToolStripDropDown activePopup;
        public event EventHandler SelectedIndexChanged;
        public bool CenterText { get; set; }

        public DarkDropDown()
        {
            BackColor = Surface;
            ForeColor = LightText;
            Cursor = Cursors.Hand;
            CenterText = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint | ControlStyles.ResizeRedraw, true);
        }

        public int SelectedIndex
        {
            get { return selectedIndex; }
            set
            {
                if (value < -1 || value >= entries.Count || selectedIndex == value) return;
                selectedIndex = value;
                Text = selectedIndex >= 0 ? entries[selectedIndex] : "";
                Invalidate();
                EventHandler handler = SelectedIndexChanged;
                if (handler != null) handler(this, EventArgs.Empty);
            }
        }

        public void SetItems(IEnumerable<string> items)
        {
            entries.Clear();
            entries.AddRange(items);
            selectedIndex = -1;
            Text = "";
            if (entries.Count > 0) SelectedIndex = 0;
        }

        public int FindExact(string value) { return entries.FindIndex(x => string.Equals(x, value, StringComparison.Ordinal)); }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (Enabled && e.Button == MouseButtons.Left) ShowPopup();
        }

        protected override void OnKeyDown(KeyEventArgs e)
        {
            base.OnKeyDown(e);
            if (e.KeyCode == Keys.Enter || e.KeyCode == Keys.Space || e.KeyCode == Keys.Down) { ShowPopup(); e.Handled = true; }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            Color parentColor = Parent == null ? Surface : Parent.BackColor;
            e.Graphics.Clear(parentColor);
            RectangleF bounds = new RectangleF(0.5f, 0.5f, Math.Max(1f, Width - 1f), Math.Max(1f, Height - 1f));
            using (GraphicsPath path = CreateRoundedPath(bounds, 7f))
            using (SolidBrush background = new SolidBrush(Enabled ? Surface : Color.FromArgb(55, 55, 55)))
            using (Pen border = new Pen(Accent, 1f))
            {
                e.Graphics.FillPath(background, path);
                e.Graphics.DrawPath(border, path);
            }
            Rectangle textBounds = CenterText
                ? new Rectangle(2, 0, Math.Max(0, Width - 4), Height)
                : new Rectangle(10, 0, Math.Max(0, Width - 38), Height);
            TextFormatFlags alignment = CenterText ? TextFormatFlags.HorizontalCenter : TextFormatFlags.Left;
            TextRenderer.DrawText(e.Graphics, Text, Font, textBounds, Enabled ? ForeColor : Color.Gray,
                alignment | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis | TextFormatFlags.NoPrefix);
            bool open = activePopup != null && activePopup.Visible;
            DrawChevron(e.Graphics, Width - 16, Height / 2, open, Enabled ? LightText : Color.Gray);
        }

        private static GraphicsPath CreateRoundedPath(RectangleF bounds, float radius)
        {
            GraphicsPath path = new GraphicsPath();
            float diameter = Math.Min(radius * 2f, Math.Min(bounds.Width, bounds.Height));
            RectangleF arc = new RectangleF(bounds.Left, bounds.Top, diameter, diameter);
            path.AddArc(arc, 180f, 90f);
            arc.X = bounds.Right - diameter;
            path.AddArc(arc, 270f, 90f);
            arc.Y = bounds.Bottom - diameter;
            path.AddArc(arc, 0f, 90f);
            arc.X = bounds.Left;
            path.AddArc(arc, 90f, 90f);
            path.CloseFigure();
            return path;
        }

        private void ShowPopup()
        {
            if (activePopup != null && activePopup.Visible)
            {
                activePopup.Close();
                return;
            }
            ToolStripDropDown popup = new ToolStripDropDown();
            activePopup = popup;
            popup.AutoSize = false;
            popup.Width = Width;
            popup.Height = (entries.Count + 1) * 29 + 4;
            popup.Padding = new Padding(2);
            popup.Margin = Padding.Empty;
            popup.BackColor = Surface;
            popup.Renderer = new DarkMenuRenderer();
            popup.Closed += delegate { if (ReferenceEquals(activePopup, popup)) activePopup = null; Invalidate(); };
            ToolStripMenuItem collapseItem = new ToolStripMenuItem(Text);
            collapseItem.AutoSize = false;
            collapseItem.Width = Width - 4;
            collapseItem.Height = 29;
            collapseItem.Font = Font;
            collapseItem.ForeColor = ForeColor;
            collapseItem.BackColor = Surface;
            collapseItem.Padding = CenterText ? Padding.Empty : new Padding(8, 0, 4, 0);
            collapseItem.TextAlign = CenterText ? ContentAlignment.MiddleCenter : ContentAlignment.MiddleLeft;
            collapseItem.Paint += delegate(object sender, PaintEventArgs e)
            {
                DrawChevron(e.Graphics, collapseItem.Width - 16, collapseItem.Height / 2, true, LightText);
            };
            collapseItem.Click += delegate { popup.Close(); };
            popup.Items.Add(collapseItem);
            for (int index = 0; index < entries.Count; index++)
            {
                int captured = index;
                ToolStripMenuItem item = new ToolStripMenuItem(entries[index]);
                item.AutoSize = false;
                item.Width = Width - 4;
                item.Height = 29;
                item.Font = Font;
                item.ForeColor = ForeColor;
                item.BackColor = Surface;
                item.Padding = CenterText ? Padding.Empty : new Padding(8, 0, 4, 0);
                item.TextAlign = CenterText ? ContentAlignment.MiddleCenter : ContentAlignment.MiddleLeft;
                item.Click += delegate { SelectedIndex = captured; popup.Close(); };
                popup.Items.Add(item);
            }
            popup.Show(this, new Point(0, 0));
            Invalidate();
        }

        private static void DrawChevron(Graphics graphics, int centerX, int centerY, bool pointsUp, Color color)
        {
            graphics.SmoothingMode = SmoothingMode.AntiAlias;
            Point[] points = pointsUp
                ? new[] { new Point(centerX - 5, centerY + 2), new Point(centerX, centerY - 3), new Point(centerX + 5, centerY + 2) }
                : new[] { new Point(centerX - 5, centerY - 2), new Point(centerX, centerY + 3), new Point(centerX + 5, centerY - 2) };
            using (Pen pen = new Pen(color, 2f))
            {
                pen.StartCap = LineCap.Round;
                pen.EndCap = LineCap.Round;
                pen.LineJoin = LineJoin.Round;
                graphics.DrawLines(pen, points);
            }
        }

        private sealed class DarkMenuRenderer : ToolStripProfessionalRenderer
        {
            public DarkMenuRenderer() : base(new DarkColorTable()) { RoundedEdges = false; }
            protected override void OnRenderMenuItemBackground(ToolStripItemRenderEventArgs e)
            {
                using (SolidBrush brush = new SolidBrush(e.Item.Selected ? Hover : Surface)) e.Graphics.FillRectangle(brush, new Rectangle(Point.Empty, e.Item.Size));
            }
            protected override void OnRenderToolStripBorder(ToolStripRenderEventArgs e)
            {
                using (Pen pen = new Pen(Accent)) e.Graphics.DrawRectangle(pen, 0, 0, e.ToolStrip.Width - 1, e.ToolStrip.Height - 1);
            }
        }

        private sealed class DarkColorTable : ProfessionalColorTable
        {
            public override Color ToolStripDropDownBackground { get { return Surface; } }
            public override Color ImageMarginGradientBegin { get { return Surface; } }
            public override Color ImageMarginGradientMiddle { get { return Surface; } }
            public override Color ImageMarginGradientEnd { get { return Surface; } }
            public override Color MenuItemSelected { get { return Hover; } }
            public override Color MenuBorder { get { return Accent; } }
        }
    }

    internal sealed class DarkTabControl : TabControl
    {
        private static readonly Color DarkBackground = Color.FromArgb(35, 35, 35);
        private static readonly Color DarkTab = Color.FromArgb(44, 44, 44);
        private static readonly Color Accent = Color.FromArgb(159, 0, 255);
        private static readonly Color LightText = Color.FromArgb(245, 245, 245);
        private readonly Font tabFont = new Font("Segoe UI", 14f, FontStyle.Bold, GraphicsUnit.Pixel);

        public DarkTabControl()
        {
            DrawMode = TabDrawMode.OwnerDrawFixed;
            SetStyle(ControlStyles.OptimizedDoubleBuffer, true);
        }

        protected override void WndProc(ref Message message)
        {
            base.WndProc(ref message);
            if (message.Msg != 0x000F || Width <= 0 || Height <= 0) return;
            using (Graphics graphics = Graphics.FromHwnd(Handle))
            {
                graphics.SmoothingMode = SmoothingMode.AntiAlias;
                int headerBottom = TabCount > 0 ? GetTabRect(0).Bottom + 3 : 32;
                using (SolidBrush background = new SolidBrush(DarkBackground))
                {
                    graphics.FillRectangle(background, 0, 0, Width, headerBottom);
                    graphics.FillRectangle(background, 0, headerBottom, 4, Height - headerBottom);
                    graphics.FillRectangle(background, Width - 4, headerBottom, 4, Height - headerBottom);
                    graphics.FillRectangle(background, 0, Height - 4, Width, 4);
                }

                for (int index = 0; index < TabCount; index++)
                {
                    Rectangle bounds = GetTabRect(index);
                    bounds.Inflate(-1, -1);
                    bool selected = index == SelectedIndex;
                    using (SolidBrush brush = new SolidBrush(selected ? Accent : DarkTab)) graphics.FillRectangle(brush, bounds);
                    TextRenderer.DrawText(graphics, TabPages[index].Text, tabFont, bounds, LightText,
                        TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
                }
            }
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing) tabFont.Dispose();
            base.Dispose(disposing);
        }
    }

    internal sealed class StyledButton : Control
    {
        private readonly Color baseColor;
        private const int CornerRadius = 6;
        private const int StandardHeight = 32;
        private bool hovering;
        private bool pressed;
        public StyledButton(string text, Color color, Font font)
        {
            Text = text; baseColor = color; Font = font; ForeColor = Color.White;
            Cursor = Cursors.Hand; TabStop = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint | ControlStyles.ResizeRedraw, true);
        }
        protected override void SetBoundsCore(int x, int y, int width, int height, BoundsSpecified specified)
        {
            base.SetBoundsCore(x, y, width, StandardHeight, specified | BoundsSpecified.Height);
        }
        protected override void OnMouseEnter(EventArgs e) { hovering = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { hovering = false; pressed = false; Invalidate(); base.OnMouseLeave(e); }
        protected override void OnMouseDown(MouseEventArgs e) { pressed = e.Button == MouseButtons.Left; Invalidate(); base.OnMouseDown(e); }
        protected override void OnMouseUp(MouseEventArgs e) { pressed = false; Invalidate(); base.OnMouseUp(e); }
        protected override void OnKeyDown(KeyEventArgs e)
        {
            base.OnKeyDown(e);
            if (Enabled && (e.KeyCode == Keys.Enter || e.KeyCode == Keys.Space)) { OnClick(EventArgs.Empty); e.Handled = true; }
        }
        protected override void OnGotFocus(EventArgs e) { base.OnGotFocus(e); Invalidate(); }
        protected override void OnLostFocus(EventArgs e) { base.OnLostFocus(e); Invalidate(); }
        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            e.Graphics.Clear(Parent == null ? Color.FromArgb(25, 25, 25) : Parent.BackColor);
            using (GraphicsPath path = ButtonPath(new Rectangle(1, 1, Math.Max(1, Width - 3), Math.Max(1, Height - 3)), CornerRadius))
            {
                Color background = Color.FromArgb(25, 25, 25);
                Color outlineColor = Enabled ? baseColor : Color.FromArgb(72, 72, 72);
                Color textColor = Enabled ? baseColor : Color.FromArgb(100, 100, 100);
                if (hovering && Enabled) background = BlendButtonColor(background, baseColor, .08f);
                if (pressed && Enabled)
                {
                    background = BlendButtonColor(Color.FromArgb(25, 25, 25), baseColor, .18f);
                    outlineColor = BlendButtonColor(baseColor, Color.White, .25f);
                    textColor = outlineColor;
                }
                using (SolidBrush fill = new SolidBrush(background)) e.Graphics.FillPath(fill, path);
                using (Pen outline = new Pen(outlineColor, 1f)) e.Graphics.DrawPath(outline, path);
                TextRenderer.DrawText(e.Graphics, Text, Font, ClientRectangle, textColor,
                    TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
            }
        }

        private static Color BlendButtonColor(Color background, Color accent, float amount)
        {
            amount = Math.Max(0f, Math.Min(1f, amount));
            return Color.FromArgb(
                (int)(background.R + (accent.R - background.R) * amount),
                (int)(background.G + (accent.G - background.G) * amount),
                (int)(background.B + (accent.B - background.B) * amount));
        }

        private static GraphicsPath ButtonPath(Rectangle bounds, int radius)
        {
            GraphicsPath path = new GraphicsPath();
            int diameter = radius * 2;
            path.AddArc(bounds.Left, bounds.Top, diameter, diameter, 180, 90);
            path.AddArc(bounds.Right - diameter, bounds.Top, diameter, diameter, 270, 90);
            path.AddArc(bounds.Right - diameter, bounds.Bottom - diameter, diameter, diameter, 0, 90);
            path.AddArc(bounds.Left, bounds.Bottom - diameter, diameter, diameter, 90, 90);
            path.CloseFigure();
            return path;
        }
    }
}
