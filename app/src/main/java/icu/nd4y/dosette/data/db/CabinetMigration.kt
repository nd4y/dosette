package icu.nd4y.dosette.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import icu.nd4y.dosette.data.db.entity.VARIANT_INVENTORY_SQL

internal val CABINET_MIGRATION =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createCabinets(db)
            moveStock(db)
            db.execSQL("ALTER TABLE dose_logs ADD COLUMN cabinetId TEXT")
            db.execSQL("ALTER TABLE dose_logs ADD COLUMN cabinetName TEXT")
            db.execSQL("UPDATE dose_logs SET cabinetId = 'main:' || profileId WHERE variantId IS NOT NULL")
            db.execSQL("CREATE VIEW `variant_inventory` AS " + VARIANT_INVENTORY_SQL)
        }
    }

private fun createCabinets(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS cabinets (id TEXT NOT NULL PRIMARY KEY, profileId TEXT NOT NULL, " +
            "name TEXT, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, archivedAt INTEGER, " +
            "FOREIGN KEY(profileId) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_cabinets_profileId ON cabinets(profileId)")
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS cabinet_selections (profileId TEXT NOT NULL PRIMARY KEY, " +
            "cabinetId TEXT NOT NULL, " +
            "FOREIGN KEY(profileId) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
            "FOREIGN KEY(cabinetId) REFERENCES cabinets(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_cabinet_selections_cabinetId ON cabinet_selections(cabinetId)")
    db.execSQL("INSERT INTO cabinets SELECT 'main:' || id, id, NULL, 0, createdAt, NULL FROM profiles")
    db.execSQL("INSERT INTO cabinet_selections SELECT id, 'main:' || id FROM profiles")
}

private fun moveStock(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE _stock_seed AS SELECT v.id AS variantId, 'main:' || m.profileId AS cabinetId, " +
            "v.currentStock, v.lastRefillAt FROM medication_variants v JOIN medications m ON m.id = v.medicationId",
    )
    db.execSQL(
        "CREATE TABLE _variant_metadata (id TEXT NOT NULL PRIMARY KEY, medicationId TEXT NOT NULL, label TEXT, " +
            "strengthValue REAL, strengthUnit TEXT, sortOrder INTEGER NOT NULL, trackingEnabled INTEGER NOT NULL, " +
            "lowStockThreshold REAL, defaultRefillAmount REAL, " +
            "FOREIGN KEY(medicationId) REFERENCES medications(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    )
    db.execSQL(
        "INSERT INTO _variant_metadata SELECT id, medicationId, label, strengthValue, strengthUnit, sortOrder, " +
            "trackingEnabled, lowStockThreshold, defaultRefillAmount FROM medication_variants",
    )
    db.execSQL("DROP TABLE medication_variants")
    db.execSQL("ALTER TABLE _variant_metadata RENAME TO medication_variants")
    db.execSQL("CREATE INDEX index_medication_variants_medicationId ON medication_variants(medicationId)")
    db.execSQL(
        "CREATE TABLE cabinet_stocks (variantId TEXT NOT NULL, cabinetId TEXT NOT NULL, currentStock REAL NOT NULL, " +
            "lastRefillAt INTEGER, PRIMARY KEY(variantId, cabinetId), " +
            "FOREIGN KEY(variantId) REFERENCES medication_variants(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
            "FOREIGN KEY(cabinetId) REFERENCES cabinets(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    )
    db.execSQL("CREATE INDEX index_cabinet_stocks_variantId ON cabinet_stocks(variantId)")
    db.execSQL("CREATE INDEX index_cabinet_stocks_cabinetId ON cabinet_stocks(cabinetId)")
    db.execSQL("INSERT INTO cabinet_stocks SELECT variantId, cabinetId, currentStock, lastRefillAt FROM _stock_seed")
    db.execSQL("DROP TABLE _stock_seed")
}
