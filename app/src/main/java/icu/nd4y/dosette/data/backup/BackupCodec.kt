package icu.nd4y.dosette.data.backup

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import icu.nd4y.dosette.domain.backup.BackupSnapshot

/** YAML (de)serialization of the backup schema; strict on unknown keys. */
object BackupCodec {
    private val yaml =
        Yaml(
            configuration =
                YamlConfiguration(
                    encodeDefaults = true,
                    strictMode = true,
                ),
        )

    fun encode(snapshot: BackupSnapshot): String = yaml.encodeToString(BackupSnapshot.serializer(), snapshot)

    fun decode(text: String): BackupSnapshot =
        runCatching { yaml.decodeFromString(BackupSnapshot.serializer(), text) }
            .getOrElse { throw BackupFormatException("Not a Dosette backup: ${it.message}", it) }
}
