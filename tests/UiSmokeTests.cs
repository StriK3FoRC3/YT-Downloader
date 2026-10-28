using System;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
namespace YTDownloader {
internal static class UiSmokeTests {
static object Field(object o,string n) { return o.GetType().GetField(n,BindingFlags.Instance|BindingFlags.NonPublic).GetValue(o); }
static void Check(bool ok,string text) { if(!ok) throw new Exception(text); Console.WriteLine("PASS: " + text); }
static void Call(object o,string n, params object[] args) { foreach(var m in o.GetType().GetMethods(BindingFlags.Instance|BindingFlags.NonPublic)) if(m.Name==n && m.GetParameters().Length==args.Length) { m.Invoke(o,args); return; } throw new MissingMethodException(n); }
static void Capture(Form f,string name) { using(Bitmap b=new Bitmap(f.Width,f.Height)) { f.DrawToBitmap(b,new Rectangle(Point.Empty,f.Size)); b.Save(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,name+".png")); } }
[STAThread] static void Main() {
Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
string preferences=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"Dependencies", "settings.ini"); byte[] originalPreferences=File.Exists(preferences) ? File.ReadAllBytes(preferences) : null; try { using(MainForm f=new MainForm()) {
f.Show(); Application.DoEvents(); Capture(f,"ui-main");
RadioButton video=(RadioButton)Field(f,"videoMode"); video.Checked=true;
Check(((Control)Field(f,"videoFormat")).Visible && !((Control)Field(f,"audioFormat")).Visible && ((Control)Field(f,"resolution")).Enabled,"Video mode exposes video format and resolution");
((RadioButton)Field(f,"audioMode")).Checked=true;
Check(((Control)Field(f,"audioFormat")).Visible && !((Control)Field(f,"resolution")).Enabled,"Audio mode restores audio options");
Call(f,"SetBusy",true); Check(!((Control)Field(f,"activeProfileBox")).Enabled && ((Control)Field(f,"cancelButton")).Enabled,"Busy state protects profile and permits cancellation"); Call(f,"SetBusy",false);
f.Size=f.MinimumSize; Application.DoEvents();
foreach(Control c in f.Controls) if(c.Visible) Check(f.ClientRectangle.Contains(c.Bounds),"Minimum-size bounds: "+c.GetType().Name+" "+c.Text);
Capture(f,"ui-minimum");
f.Size=new Size(1400,900); Application.DoEvents(); Capture(f,"ui-wide");
Form settings=(Form)Field(f,"settingsWindow"); settings.Show(f); Application.DoEvents();
TabControl tabs=null; foreach(Control c in settings.Controls) if(c is TabControl) tabs=(TabControl)c;
Check(tabs!=null && tabs.TabCount==3,"Three profile editor sections");
for(int i=0;i<tabs.TabCount;i++) { tabs.SelectedIndex=i; Application.DoEvents(); foreach(Control c in tabs.SelectedTab.Controls) if(c.Visible) Check(tabs.SelectedTab.ClientRectangle.Contains(c.Bounds),"Settings bounds: "+c.Text); Capture(settings,"ui-settings-"+i); }
var thumbnail=(AccentCheckBox)Field(f,"thumbnailToggle");
bool originalThumbnail=thumbnail.Checked; thumbnail.Checked=!originalThumbnail; Call(f,"SavePreferences"); Call(f,"LoadPreferences");
Check(thumbnail.Checked!=originalThumbnail,"Profile thumbnail setting survives save and reload"); thumbnail.Checked=originalThumbnail; Call(f,"SavePreferences");
var cleanupToggle=(AccentCheckBox)Field(f,"titleFilterToggle"); bool originalCleanup=cleanupToggle.Checked; cleanupToggle.Checked=true;
Check(((Control)Field(f,"filterRulesBox")).Enabled,"Title cleanup controls enable from their new tab"); cleanupToggle.Checked=originalCleanup;
var metadata=(AccentCheckBox)Field(f,"metadataToggle"); bool originalMetadata=metadata.Checked; metadata.Checked=true;
Check(((Control)Field(f,"metadataTitleToggle")).Enabled,"Metadata fields enable from their new tab"); metadata.Checked=originalMetadata;
var activity=(ListView)Field(f,"downloads"); activity.Items.Add(new ListViewItem(new [] {"Example download", "Audio", "Finished", "https://example.com"})); Call(f,"EnsureColumnsFit",activity,-1);
foreach(Control c in activity.Controls) if(object.Equals(c.Tag,"empty-state")) Check(!c.Visible,"Empty state hides for activity rows");
activity.Items.Clear();
for(int i=0;i<100;i++) activity.Items.Add(new ListViewItem(new [] {"Download "+i,"Audio","Complete","https://example.com"}));
Call(f,"EnsureColumnsFit",activity,-1); activity.Refresh(); Application.DoEvents();
var listScroll=(DarkVerticalScrollBar)Field(activity,"scrollbar");
Check(listScroll.Visible,"Modern activity scrollbar appears on overflow");
Call(listScroll,"SetValue",30); Application.DoEvents(); Check(activity.TopItem.Index==30,"Activity scrollbar scrolls to requested row");
Capture(f,"ui-scrollbars"); activity.Items.Clear(); Call(f,"EnsureColumnsFit",activity,-1);
var input=(TextBox)Field(f,"urlBox");
input.Text=string.Join("\r\n",System.Linq.Enumerable.Select(System.Linq.Enumerable.Range(0,30),i=>"https://example.com/video/"+i));
Application.DoEvents(); var inputScroll=(DarkVerticalScrollBar)Field(input,"scrollbar");
Check(inputScroll.Visible,"Modern link scrollbar appears on overflow"); Call(inputScroll,"SetValue",12);
Check(inputScroll.Value==12,"Link scrollbar retains requested scroll position");
Capture(f,"ui-links-scrollbar"); input.Clear(); Application.DoEvents(); Check(!inputScroll.Visible,"Link scrollbar hides when empty");
((TextBox)Field(f,"urlBox")).Text="https://www.youtube.com/watch?v=test\r\nhttps://www.youtube.com/watch?v=test";
var links=(System.Collections.Generic.List<string>)f.GetType().GetMethod("GetLinks",BindingFlags.Instance|BindingFlags.NonPublic).Invoke(f,null); Check(links.Count==1,"Duplicate links still deduplicated");
f.Size = new Size(900, 600); Call(f,"SavePreferences");
using(MainForm restored = new MainForm()) { Check(restored.Size == new Size(900,600), "Window size restores in a new app instance"); }
f.WindowState = FormWindowState.Minimized; Call(f,"SavePreferences");
Check(File.ReadAllText(preferences).Contains("WindowWidth=900") && File.ReadAllText(preferences).Contains("WindowHeight=600"), "Minimizing preserves the normal window size");
f.WindowState = FormWindowState.Normal;
((TextBox)Field(f,"urlBox")).Clear(); settings.Hide(); f.Close();
}
} finally { if(originalPreferences!=null) File.WriteAllBytes(preferences,originalPreferences); else if(File.Exists(preferences)) File.Delete(preferences); } Console.WriteLine("UI smoke tests completed.");
}
}}


