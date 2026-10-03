using System.Reflection.Metadata;
using System.Reflection.PortableExecutable;

namespace Cogfly.Downloader;

/// <summary>Reads Constants.GAME_VERSION out of Assembly-CSharp.dll without loading the assembly.</summary>
public static class Identifier
{
    static readonly string[] ManagedDirs =
    [
        Path.Combine("Hollow Knight Silksong_Data", "Managed"),
        Path.Combine("Hollow Knight Silksong.app", "Contents", "Resources", "Data", "Managed"),
    ];

    /// <param name="path">Assembly-CSharp.dll, or a game folder containing it.</param>
    public static string? GetGameVersion(string path)
    {
        if (Directory.Exists(path))
        {
            path = ManagedDirs
                .Select(dir => Path.Combine(path, dir, "Assembly-CSharp.dll"))
                .FirstOrDefault(File.Exists) ?? "";
        }

        if (!File.Exists(path))
            return null;

        using var stream = File.OpenRead(path);
        using var pe = new PEReader(stream);
        if (!pe.HasMetadata)
            return null;

        var md = pe.GetMetadataReader();
        foreach (var typeHandle in md.TypeDefinitions)
        {
            var type = md.GetTypeDefinition(typeHandle);
            if (!type.Namespace.IsNil || md.GetString(type.Name) != "Constants")
                continue;

            foreach (var fieldHandle in type.GetFields())
            {
                var field = md.GetFieldDefinition(fieldHandle);
                if (md.GetString(field.Name) != "GAME_VERSION")
                    continue;

                var constantHandle = field.GetDefaultValue();
                if (constantHandle.IsNil)
                    return null;

                var constant = md.GetConstant(constantHandle);
                if (constant.TypeCode != ConstantTypeCode.String)
                    return null;

                return md.GetBlobReader(constant.Value).ReadConstant(ConstantTypeCode.String) as string;
            }
        }

        return null;
    }
}
