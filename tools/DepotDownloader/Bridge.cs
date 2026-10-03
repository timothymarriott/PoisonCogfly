// This file is part of the Cogfly modifications to DepotDownloader
// (https://github.com/SteamRE/DepotDownloader), licensed under GPL-2.0
// (see LICENSE in this directory). Added by the Cogfly project on 2026-10-03.
// See NOTICE.md.

#nullable enable
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;

namespace DepotDownloader;

/// <summary>
/// Line-based JSON protocol used by Cogfly to drive the downloader. Events go out on stdout, one JSON object per
/// line; answers to prompts come in on stdin as {"value": "..."}. Everything else the library prints is routed to
/// stderr so it can never corrupt the protocol stream.
/// </summary>
public static class Bridge
{
    static TextWriter proto = TextWriter.Null;
    static TextReader input = TextReader.Null;
    static readonly object outLock = new();
    static readonly object inLock = new();
    static RingWriter? errorRing;
    static long lastProgressTicks;
    static int lastProgressPermille = -1;

    public static void Init()
    {
        proto = new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false)) { AutoFlush = true };
        input = new StreamReader(Console.OpenStandardInput(), new UTF8Encoding(false));
        errorRing = new RingWriter(new StreamWriter(Console.OpenStandardError(), new UTF8Encoding(false)) { AutoFlush = true });
        Console.SetError(errorRing);
        Console.SetOut(errorRing);
    }

    /// <summary>The last few lines the library printed, used to explain a failure.</summary>
    public static string RecentOutput() => errorRing?.Recent() ?? "";

    public static void Emit(string ev, params (string Key, object? Value)[] data)
    {
        var obj = new Dictionary<string, object?> { ["event"] = ev };
        foreach (var (key, value) in data)
            obj[key] = value;
        lock (outLock)
            proto.WriteLine(JsonSerializer.Serialize(obj));
    }

    /// <summary>Asks Cogfly for a value (password, guard code, ...). Returns null if stdin closed.</summary>
    public static string? Request(string kind, params (string Key, object? Value)[] data)
    {
        Emit("prompt", data.Append(("kind", kind)).ToArray());
        string? line;
        lock (inLock)
            line = input.ReadLine();
        if (line == null)
            return null;
        try
        {
            using var doc = JsonDocument.Parse(line);
            return doc.RootElement.TryGetProperty("value", out var v) ? v.GetString() : null;
        }
        catch (JsonException)
        {
            return null;
        }
    }

    public static void Progress(long downloaded, long total)
    {
        if (total <= 0)
            return;
        int permille = (int)(downloaded * 1000 / total);
        long now = Environment.TickCount64;
        lock (outLock)
        {
            if (permille == lastProgressPermille || (permille < 1000 && now - lastProgressTicks < 100))
                return;
            lastProgressPermille = permille;
            lastProgressTicks = now;
        }
        Emit("progress", ("downloaded", downloaded), ("total", total));
    }

    sealed class RingWriter(TextWriter inner) : TextWriter
    {
        readonly Queue<string> lines = new();
        readonly StringBuilder pending = new();
        public override Encoding Encoding => inner.Encoding;

        public override void Write(char value)
        {
            lock (lines)
            {
                if (value == '\n')
                {
                    var line = pending.ToString().Trim();
                    pending.Clear();
                    if (line.Length > 0)
                    {
                        lines.Enqueue(line);
                        while (lines.Count > 6)
                            lines.Dequeue();
                    }
                }
                else
                {
                    pending.Append(value);
                }
            }
            inner.Write(value);
        }

        public string Recent()
        {
            lock (lines)
                return string.Join("\n", lines);
        }
    }
}
