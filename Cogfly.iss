#define app "PoisonCogfly"
#define link "https://github.com/timothymarriott/PoisonCogfly"
#define exe "PoisonCogfly.exe"

[Setup]
AppId={{7B1F4E52-3A9C-4D86-9E0B-5C2D8A61F3E7}
AppName={#app}
AppVerName={#app}
AppVersion={#cgver}
UsePreviousAppDir=yes
DisableDirPage=auto
SetupIconFile=resources\icons\icon.ico
AppPublisher="Timothy Marriott"
AppPublisherURL={#link}
AppSupportURL={#link}
AppUpdatesURL={#link}
OutputBaseFilename=PoisonCogfly-{#cgver}-installer
DefaultDirName={autopf}\{#app}
UninstallDisplayIcon={app}\{#exe}
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes
UsePreviousTasks=yes
UsePreviousLanguage=yes
UsePreviousSetupType=yes
LicenseFile=LICENSE
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
SolidCompression=yes
WizardStyle=modern dark

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "output\Windows\PoisonCogfly\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "resources\icons\icon.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#app}"; Filename: "{app}\{#exe}"
Name: "{autodesktop}\{#app}"; Filename: "{app}\{#exe}"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Classes\cogfly"; ValueType: string; ValueName: ""; ValueData: "URL:PoisonCogfly Protocol"; Flags: uninsdeletekey; Check: not IsAdminInstall
Root: HKLM; Subkey: "Software\Classes\cogfly"; ValueType: string; ValueName: ""; ValueData: "URL:PoisonCogfly Protocol"; Flags: uninsdeletekey; Check: IsAdminInstall
Root: HKCU; Subkey: "Software\Classes\cogfly"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""; Check: not IsAdminInstall
Root: HKLM; Subkey: "Software\Classes\cogfly"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""; Check: IsAdminInstall
Root: HKCU; Subkey: "Software\Classes\cogfly\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\PoisonCogfly.exe"" ""%1"""; Check: not IsAdminInstall
Root: HKLM; Subkey: "Software\Classes\cogfly\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\PoisonCogfly.exe"" ""%1"""; Check: IsAdminInstall
[Run]
Filename: "{app}\{#exe}"; Description: "{cm:LaunchProgram,{#StringChange(app, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
function IsAdminInstall: Boolean;
begin
  Result := IsAdminInstallMode;
end;
