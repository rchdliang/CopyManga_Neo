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

[Icons]
Name: "{userprograms}\CopyManga Neo"; Filename: "{app}\CopyMangaNeo.exe"; WorkingDir: "{app}"
Name: "{userdesktop}\CopyManga Neo"; Filename: "{app}\CopyMangaNeo.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\CopyMangaNeo.exe"; WorkingDir: "{app}"; Description: "{cm:LaunchApp}"; Flags: nowait postinstall skipifsilent
