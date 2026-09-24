package dev.draftingroom5.retirement.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.draftingroom5.retirement.domain.RetirementState
import java.io.Closeable
import java.io.File

data class RetirementStateEntity(val singletonId: Int = 1, val generation: Long, val document: String)
data class BalanceLedgerEntity(val id: String, val accountId: String, val asOfDate: String, val acceptedAt: String, val sequence: Long,
    val amountCents: Long, val basisCents: Long?, val source: String, val batchId: String, val supersedesId: String?)
data class ValuationLedgerEntity(val id: String, val propertyId: String, val estimateCents: Long, val rangeLowCents: Long?, val rangeHighCents: Long?,
    val source: String, val acceptedAt: String, val sequence: Long, val batchId: String, val supersedesId: String?)
data class EpicImportLedgerEntity(val id: String, val formatId: String, val parserVersion: Int, val acceptedAt: String, val contentDigest: String, val replacesImportId: String?)

interface RetirementDao {
    fun state(): RetirementStateEntity?
    fun initialize(value: RetirementStateEntity): Long
    fun compareAndSet(expectedGeneration: Long, newGeneration: Long, document: String): Int
    fun appendBalances(values: List<BalanceLedgerEntity>)
    fun appendValuations(values: List<ValuationLedgerEntity>)
    fun appendEpicImports(values: List<EpicImportLedgerEntity>)
    fun balanceCount(): Int
    fun valuationCount(): Int
    fun epicImportCount(): Int
    fun commit(expectedGeneration: Long, state: RetirementState, balances: List<BalanceLedgerEntity>, valuations: List<ValuationLedgerEntity>, imports: List<EpicImportLedgerEntity>): Boolean
    fun restore(expectedGeneration: Long, state: RetirementState, balances: List<BalanceLedgerEntity>, valuations: List<ValuationLedgerEntity>, imports: List<EpicImportLedgerEntity>): Boolean =
        compareAndSet(expectedGeneration, state.generation, RetirementCodec.encode(state)) == 1
}

class RetirementDatabase private constructor(private val helper: Helper) : Closeable {
    val dao: RetirementDao = SQLiteRetirementDao(helper)
    override fun close() = helper.close()

    companion object {
        fun open(context: Context): RetirementDatabase {
            val directory = File(context.noBackupFilesDir, "retirement")
            check(directory.exists() || directory.mkdirs()) { "Could not create Retirement storage." }
            return RetirementDatabase(Helper(context, File(directory, "retirement.db").absolutePath).also { it.setWriteAheadLoggingEnabled(true) })
        }
    }

    private class Helper(context: Context, path: String) : SQLiteOpenHelper(context, path, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Clean-slate Retirement schema has no migrations.")
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE retirement_state(singletonId INTEGER PRIMARY KEY CHECK(singletonId=1), generation INTEGER NOT NULL CHECK(generation>=0), document TEXT NOT NULL)")
            db.execSQL("CREATE TABLE balance_snapshots(id TEXT PRIMARY KEY, accountId TEXT NOT NULL, asOfDate TEXT NOT NULL, acceptedAt TEXT NOT NULL, sequence INTEGER NOT NULL CHECK(sequence>=0), amountCents INTEGER NOT NULL, basisCents INTEGER, source TEXT NOT NULL CHECK(source IN ('MANUAL','PLAID')), batchId TEXT NOT NULL, supersedesId TEXT, UNIQUE(accountId,asOfDate,sequence), FOREIGN KEY(supersedesId) REFERENCES balance_snapshots(id))")
            db.execSQL("CREATE TABLE property_valuations(id TEXT PRIMARY KEY, propertyId TEXT NOT NULL, estimateCents INTEGER NOT NULL CHECK(estimateCents>=0), rangeLowCents INTEGER, rangeHighCents INTEGER, source TEXT NOT NULL CHECK(source IN ('MANUAL','RENTCAST')), acceptedAt TEXT NOT NULL, sequence INTEGER NOT NULL CHECK(sequence>=0), batchId TEXT NOT NULL, supersedesId TEXT, CHECK((rangeLowCents IS NULL AND rangeHighCents IS NULL) OR (rangeLowCents<=estimateCents AND estimateCents<=rangeHighCents)), UNIQUE(propertyId,sequence), FOREIGN KEY(supersedesId) REFERENCES property_valuations(id))")
            db.execSQL("CREATE TABLE epic_imports(id TEXT PRIMARY KEY, formatId TEXT NOT NULL, parserVersion INTEGER NOT NULL CHECK(parserVersion>0), acceptedAt TEXT NOT NULL, contentDigest TEXT NOT NULL CHECK(length(contentDigest)=64), replacesImportId TEXT, FOREIGN KEY(replacesImportId) REFERENCES epic_imports(id))")
            installAppendOnlyTriggers(db)
        }
        override fun onOpen(db: SQLiteDatabase) { super.onOpen(db); installAppendOnlyTriggers(db) }
        private fun installAppendOnlyTriggers(db: SQLiteDatabase) {
            listOf("balance_snapshots", "property_valuations", "epic_imports").forEach { table ->
                db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_no_update BEFORE UPDATE ON $table BEGIN SELECT RAISE(ABORT, '$table is append-only'); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS ${table}_no_delete BEFORE DELETE ON $table BEGIN SELECT RAISE(ABORT, '$table is append-only'); END")
            }
        }
    }

    private class SQLiteRetirementDao(private val helper: Helper) : RetirementDao {
        override fun state(): RetirementStateEntity? = helper.readableDatabase.rawQuery("SELECT generation,document FROM retirement_state WHERE singletonId=1", null).use { cursor ->
            if (!cursor.moveToFirst()) null else RetirementStateEntity(generation = cursor.getLong(0), document = cursor.getString(1))
        }
        override fun initialize(value: RetirementStateEntity): Long = helper.writableDatabase.insertWithOnConflict("retirement_state", null,
            ContentValues().apply { put("singletonId", 1); put("generation", value.generation); put("document", value.document) }, SQLiteDatabase.CONFLICT_IGNORE)
        override fun compareAndSet(expectedGeneration: Long, newGeneration: Long, document: String): Int = helper.writableDatabase.update("retirement_state",
            ContentValues().apply { put("generation", newGeneration); put("document", document) }, "singletonId=1 AND generation=?", arrayOf(expectedGeneration.toString()))
        override fun appendBalances(values: List<BalanceLedgerEntity>) = values.forEach { value -> insert("balance_snapshots", ContentValues().apply {
            put("id", value.id); put("accountId", value.accountId); put("asOfDate", value.asOfDate); put("acceptedAt", value.acceptedAt); put("sequence", value.sequence)
            put("amountCents", value.amountCents); value.basisCents?.let { put("basisCents", it) }; put("source", value.source); put("batchId", value.batchId); value.supersedesId?.let { put("supersedesId", it) }
        }) }
        override fun appendValuations(values: List<ValuationLedgerEntity>) = values.forEach { value -> insert("property_valuations", ContentValues().apply {
            put("id", value.id); put("propertyId", value.propertyId); put("estimateCents", value.estimateCents); value.rangeLowCents?.let { put("rangeLowCents", it) }; value.rangeHighCents?.let { put("rangeHighCents", it) }
            put("source", value.source); put("acceptedAt", value.acceptedAt); put("sequence", value.sequence); put("batchId", value.batchId); value.supersedesId?.let { put("supersedesId", it) }
        }) }
        override fun appendEpicImports(values: List<EpicImportLedgerEntity>) = values.forEach { value -> insert("epic_imports", ContentValues().apply {
            put("id", value.id); put("formatId", value.formatId); put("parserVersion", value.parserVersion); put("acceptedAt", value.acceptedAt); put("contentDigest", value.contentDigest); value.replacesImportId?.let { put("replacesImportId", it) }
        }) }
        override fun balanceCount() = count("balance_snapshots")
        override fun valuationCount() = count("property_valuations")
        override fun epicImportCount() = count("epic_imports")
        override fun commit(expectedGeneration: Long, state: RetirementState, balances: List<BalanceLedgerEntity>, valuations: List<ValuationLedgerEntity>, imports: List<EpicImportLedgerEntity>): Boolean {
            val db = helper.writableDatabase
            db.beginTransaction()
            return try {
                appendBalances(balances); appendValuations(valuations); appendEpicImports(imports)
                val committed = compareAndSet(expectedGeneration, state.generation, RetirementCodec.encode(state)) == 1
                if (committed) db.setTransactionSuccessful()
                committed
            } finally { db.endTransaction() }
        }
        override fun restore(expectedGeneration: Long, state: RetirementState, balances: List<BalanceLedgerEntity>, valuations: List<ValuationLedgerEntity>, imports: List<EpicImportLedgerEntity>): Boolean {
            val db = helper.writableDatabase
            db.beginTransaction()
            return try {
                balances.forEach { value -> insertOrIgnore("balance_snapshots", ContentValues().apply {
                    put("id", value.id); put("accountId", value.accountId); put("asOfDate", value.asOfDate); put("acceptedAt", value.acceptedAt); put("sequence", value.sequence)
                    put("amountCents", value.amountCents); value.basisCents?.let { put("basisCents", it) }; put("source", value.source); put("batchId", value.batchId); value.supersedesId?.let { put("supersedesId", it) }
                }) }
                valuations.forEach { value -> insertOrIgnore("property_valuations", ContentValues().apply {
                    put("id", value.id); put("propertyId", value.propertyId); put("estimateCents", value.estimateCents); value.rangeLowCents?.let { put("rangeLowCents", it) }; value.rangeHighCents?.let { put("rangeHighCents", it) }
                    put("source", value.source); put("acceptedAt", value.acceptedAt); put("sequence", value.sequence); put("batchId", value.batchId); value.supersedesId?.let { put("supersedesId", it) }
                }) }
                imports.forEach { value -> insertOrIgnore("epic_imports", ContentValues().apply {
                    put("id", value.id); put("formatId", value.formatId); put("parserVersion", value.parserVersion); put("acceptedAt", value.acceptedAt); put("contentDigest", value.contentDigest); value.replacesImportId?.let { put("replacesImportId", it) }
                }) }
                val restored = compareAndSet(expectedGeneration, state.generation, RetirementCodec.encode(state)) == 1
                if (restored) db.setTransactionSuccessful()
                restored
            } finally { db.endTransaction() }
        }
        private fun insert(table: String, values: ContentValues) { check(helper.writableDatabase.insertOrThrow(table, null, values) != -1L) }
        private fun insertOrIgnore(table: String, values: ContentValues) {
            check(helper.writableDatabase.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L)
        }
        private fun count(table: String) = helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }
    }
}
