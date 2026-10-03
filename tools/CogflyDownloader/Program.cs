using Cogfly.Downloader;
using DepotDownloader;

/*
 * Helper executable bundled with Cogfly.
 *
 *   CogflyDownloader download --app N --depot N --manifest N --branch B --dir DIR --config DIR [--username U]
 *       Downloads one depot manifest. Speaks the JSON line protocol described in Bridge.cs.
 *   CogflyDownloader identify <game folder | Assembly-CSharp.dll>
 *       Prints the game version and exits 0, or exits 1 if it can't be determined.
 *   CogflyDownloader logout --config DIR [--username U]
 *       Forgets the stored Steam login.
 */
static class Program
{
    static async Task<int> Main(string[] args)
    {
        if (args.Length == 0)
            return Usage();

        var options = ParseOptions(args.Skip(1).ToArray());
        switch (args[0])
        {
            case "identify":
            {
                var target = args.Length > 1 ? args[1] : "";
                var version = Identifier.GetGameVersion(target);
                if (string.IsNullOrEmpty(version))
                {
                    Console.Error.WriteLine("Could not determine the game version.");
                    return 1;
                }
                Console.WriteLine(version);
                return 0;
            }
            case "logout":
                AppDownloader.Logout(Required(options, "config"), options.GetValueOrDefault("username", ""));
                return 0;
            case "download":
                return await Download(options);
            default:
                return Usage();
        }
    }

    static async Task<int> Download(Dictionary<string, string> o)
    {
        Bridge.Init();
        try
        {
            int code = await AppDownloader.Download(
                uint.Parse(Required(o, "app")),
                uint.Parse(Required(o, "depot")),
                ulong.Parse(Required(o, "manifest")),
                o.GetValueOrDefault("branch", "public"),
                Required(o, "dir"),
                Required(o, "config"),
                o.GetValueOrDefault("username", ""));
            if (code == 0)
            {
                Bridge.Emit("done");
                return 0;
            }
            var reason = code == 2 ? "Steam login failed." : "The download failed.";
            Bridge.Emit("error", ("message", reason + "\n" + Bridge.RecentOutput()));
            return code;
        }
        catch (Exception e)
        {
            Bridge.Emit("error", ("message", e.Message));
            return 1;
        }
    }

    static Dictionary<string, string> ParseOptions(string[] args)
    {
        var options = new Dictionary<string, string>();
        for (int i = 0; i < args.Length; i++)
        {
            if (args[i].StartsWith("--") && i + 1 < args.Length)
                options[args[i][2..]] = args[++i];
        }
        return options;
    }

    static string Required(Dictionary<string, string> options, string key) =>
        options.TryGetValue(key, out var value) ? value : throw new ArgumentException($"Missing --{key}");

    static int Usage()
    {
        Console.Error.WriteLine("Usage: CogflyDownloader <download|identify|logout> ...");
        return 64;
    }
}
