; 简体中文翻译来自 jrsoftware/issrc 的 is-6_7_1 标签：
; Files/Languages/Unofficial/ChineseSimplified.isl，保留原作者声明。
#if VER < EncodeVer(6, 5, 0)
  #error Inno Setup 6.5 or later is required.
#endif
#ifndef AppImage
  #error AppImage is required.
#endif
#ifndef AppVersion
  #error AppVersion is required.
#endif
#ifndef AppArchitecture
  #error AppArchitecture is required.
#endif
#ifndef OutputDirectory
  #error OutputDirectory is required.
#endif
#ifndef AppRuntime
  #define AppRuntime "jvm"
#endif

[Setup]
; 固定标识保证后续 Inno Setup 版本覆盖安装到同一目录。
AppId=com.rchdliang.copymanga_neo.desktop
AppName=CopyManga Neo
AppVersion={#AppVersion}
AppPublisher=rchdliang
DefaultDirName={localappdata}\Programs\CopyMangaNeo
DefaultGroupName=CopyManga Neo
PrivilegesRequired=lowest
#if AppArchitecture == "x64"
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
#else
ArchitecturesAllowed=arm64
ArchitecturesInstallIn64BitMode=arm64
#endif
MinVersion=10.0
DisableDirPage=no
DisableProgramGroupPage=yes
DisableWelcomePage=no
DisableFinishedPage=no
WizardStyle=modern
SetupIconFile=..\icons\copymanga-neo.ico
UninstallDisplayIcon={app}\CopyMangaNeo.exe
OutputDir={#OutputDirectory}
OutputBaseFilename=CopyMangaNeo-{#AppVersion}-windows-{#AppArchitecture}-setup
Compression=lzma2
SolidCompression=yes
CloseApplications=yes
RestartApplications=no
SetupLogging=yes

[Languages]
Name: "chinesesimplified"; MessagesFile: "languages\ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
chinesesimplified.LaunchApp=运行 CopyManga Neo
english.LaunchApp=Run CopyManga Neo

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

#if AppRuntime == "native"
[InstallDelete]
; 仅清理确认属于旧 jpackage 版本的程序文件；用户资料位于独立目录。
Type: filesandordirs; Name: "{app}\runtime"; Check: HasLegacyJvmImage
Type: filesandordirs; Name: "{app}\app"; Check: HasLegacyJvmImage

[Code]
function HasLegacyJvmImage: Boolean;
begin
  Result := FileExists(ExpandConstant('{app}\app\CopyMangaNeo.cfg'));
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
    if not SaveStringToFile(ExpandConstant('{app}\.nucleus-executable-type'),
      'exe' + #13#10 + '{#AppVersion}' + #13#10, False) then
      RaiseException('Cannot write native application metadata.');
end;

[UninstallDelete]
Type: files; Name: "{app}\.nucleus-executable-type"
#endif

[Icons]
Name: "{userprograms}\CopyManga Neo"; Filename: "{app}\CopyMangaNeo.exe"; WorkingDir: "{app}"; AppUserModelID: "com.rchdliang.copymanga_neo.desktop"
Name: "{userdesktop}\CopyManga Neo"; Filename: "{app}\CopyMangaNeo.exe"; WorkingDir: "{app}"; Tasks: desktopicon; AppUserModelID: "com.rchdliang.copymanga_neo.desktop"

[Run]
Filename: "{app}\CopyMangaNeo.exe"; WorkingDir: "{app}"; Description: "{cm:LaunchApp}"; Flags: nowait postinstall skipifsilent
