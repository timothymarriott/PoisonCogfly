// This file is part of DepotDownloader (https://github.com/SteamRE/DepotDownloader),
// licensed under GPL-2.0 (see LICENSE in this directory).
// Modified by the Cogfly project on 2026-10-03; based on upstream 3.4.0 (c553ef4).
// See NOTICE.md for a summary of the changes.

// This file is subject to the terms and conditions defined
// in file 'LICENSE', which is part of this source code package.

using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using ProtoBuf;

namespace DepotDownloader
{
    [ProtoContract]
    class AccountSettingsStore
    {
        // Member 1 was a Dictionary<string, byte[]> for SentryData.

        [ProtoMember(2, IsRequired = false)]
        public ConcurrentDictionary<string, int> ContentServerPenalty { get; private set; }

        // Member 3 was a Dictionary<string, string> for LoginKeys.

        [ProtoMember(4, IsRequired = false)]
        public Dictionary<string, string> LoginTokens { get; private set; }

        [ProtoMember(5, IsRequired = false)]
        public Dictionary<string, string> GuardData { get; private set; }

        string FileName;

        AccountSettingsStore()
        {
            ContentServerPenalty = new ConcurrentDictionary<string, int>();
            LoginTokens = new(StringComparer.OrdinalIgnoreCase);
            GuardData = new(StringComparer.OrdinalIgnoreCase);
        }

        static bool Loaded
        {
            get { return Instance != null; }
        }

        public static AccountSettingsStore Instance;

        /// <summary>Folder holding account.store; set by the host before LoadFromFile.</summary>
        public static string ConfigDir = ".";

        public static void LoadFromFile()
        {
            if (Loaded)
                throw new Exception("Config already loaded");

            var fileName = Path.Combine(ConfigDir, "account.store");
            AccountSettingsStore loaded = null;
            if (File.Exists(fileName))
            {
                try
                {
                    using var fs = File.OpenRead(fileName);
                    using var ds = new DeflateStream(fs, CompressionMode.Decompress);
                    loaded = Serializer.Deserialize<AccountSettingsStore>(ds);
                }
                catch (Exception ex)
                {
                    Console.WriteLine("Failed to read account settings: {0}", ex.Message);
                }
            }

            Instance = loaded ?? new AccountSettingsStore();
            Instance.FileName = fileName;
        }

        public static void Save()
        {
            if (!Loaded)
                throw new Exception("Saved config before loading");

            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(Instance.FileName)!);
                using var fs = File.Open(Instance.FileName, FileMode.Create, FileAccess.Write);
                using var ds = new DeflateStream(fs, CompressionMode.Compress);
                Serializer.Serialize(ds, Instance);
            }
            catch (IOException ex)
            {
                Console.WriteLine("Failed to save account settings: {0}", ex.Message);
            }
        }
    }
}
