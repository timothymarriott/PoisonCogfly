// This file is part of DepotDownloader (https://github.com/SteamRE/DepotDownloader),
// licensed under GPL-2.0 (see LICENSE in this directory).
// Modified by the Cogfly project on 2026-10-03; based on upstream 3.4.0 (c553ef4).
// See NOTICE.md for a summary of the changes.

using System;
using System.Threading.Tasks;
using SteamKit2;

namespace DepotDownloader;

public static class AppDownloader
{
    /// <returns>0 on success, 2 if login failed, 1 for any other failure.</returns>
    public static async Task<int> Download(uint appid, uint depot, ulong manifest, string branch, string dir, string configDir, string username)
    {
        DebugLog.Enabled = false;
        AccountSettingsStore.ConfigDir = configDir;
        AccountSettingsStore.LoadFromFile();

        ContentDownloader.Config.RememberPassword = true;
        ContentDownloader.Config.SkipAppConfirmation = false;
        ContentDownloader.Config.CellID = 0;
        ContentDownloader.Config.VerifyAll = false;
        ContentDownloader.Config.MaxDownloads = 8;
        ContentDownloader.Config.BetaPassword = "";
        ContentDownloader.Config.DownloadAllPlatforms = false;
        ContentDownloader.Config.DownloadAllArchs = false;
        ContentDownloader.Config.InstallDirectory = dir;

        if (!ContentDownloader.InitializeSteam3(username))
            return 2;

        try
        {
            await ContentDownloader.DownloadAppAsync(appid, depot, manifest, branch, null, null, null, false, false).ConfigureAwait(false);
            return 0;
        }
        catch (Exception ex) when (ex is ContentDownloaderException || ex is OperationCanceledException)
        {
            Console.WriteLine(ex.Message);
            return 1;
        }
        finally
        {
            ContentDownloader.ShutdownSteam3();
        }
    }

    /// <summary>Forgets the stored login token of one account, or of every account when username is empty.</summary>
    public static void Logout(string configDir, string username)
    {
        AccountSettingsStore.ConfigDir = configDir;
        AccountSettingsStore.LoadFromFile();
        var store = AccountSettingsStore.Instance;
        if (string.IsNullOrEmpty(username))
        {
            store.LoginTokens.Clear();
            store.GuardData.Clear();
        }
        else
        {
            store.LoginTokens.Remove(username);
            store.GuardData.Remove(username);
        }
        AccountSettingsStore.Save();
    }
}
