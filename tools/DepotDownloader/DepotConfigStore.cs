// This file is part of DepotDownloader (https://github.com/SteamRE/DepotDownloader),
// licensed under GPL-2.0 (see LICENSE in this directory).
// Modified by the Cogfly project on 2026-10-03; based on upstream 3.4.0 (c553ef4).
// See NOTICE.md for a summary of the changes.

// This file is subject to the terms and conditions defined
// in file 'LICENSE', which is part of this source code package.

using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using ProtoBuf;

namespace DepotDownloader
{
    [ProtoContract]
    class DepotConfigStore
    {
        [ProtoMember(1)]
        public Dictionary<uint, ulong> InstalledManifestIDs { get; private set; }

        string FileName;

        DepotConfigStore()
        {
            InstalledManifestIDs = [];
        }

        


    }
}
