// This file is part of DepotDownloader (https://github.com/SteamRE/DepotDownloader),
// licensed under GPL-2.0 (see LICENSE in this directory).
// Modified by the Cogfly project on 2026-10-03; based on upstream 3.4.0 (c553ef4).
// See NOTICE.md for a summary of the changes.

// This file is subject to the terms and conditions defined
// in file 'LICENSE', which is part of this source code package.

using System;
using System.Threading.Tasks;
using SteamKit2.Authentication;

namespace DepotDownloader
{
    // This is practically copied from https://github.com/SteamRE/SteamKit/blob/master/SteamKit2/SteamKit2/Steam/Authentication/UserConsoleAuthenticator.cs
    internal class ConsoleAuthenticator : IAuthenticator
    {
        /// <inheritdoc />
        public Task<string> GetDeviceCodeAsync(bool previousCodeWasIncorrect)
        {
            if (previousCodeWasIncorrect)
            {
                Console.Error.WriteLine("The previous 2-factor auth code you have provided is incorrect.");
            }

            var code = Bridge.Request("guard_device", ("retry", previousCodeWasIncorrect))?.Trim();
            return Task.FromResult(code!);
        }

        /// <inheritdoc />
        public Task<string> GetEmailCodeAsync(string email, bool previousCodeWasIncorrect)
        {
            if (previousCodeWasIncorrect)
            {
                Console.Error.WriteLine("The previous 2-factor auth code you have provided is incorrect.");
            }

            var code = Bridge.Request("guard_email", ("email", email), ("retry", previousCodeWasIncorrect))?.Trim();
            return Task.FromResult(code!);
        }

        /// <inheritdoc />
        public Task<bool> AcceptDeviceConfirmationAsync()
        {
            if (ContentDownloader.Config.SkipAppConfirmation)
            {
                return Task.FromResult(false);
            }

            Bridge.Emit("confirm", ("message", "Approve the sign-in request in your Steam mobile app."));

            return Task.FromResult(true);
        }
    }
}
