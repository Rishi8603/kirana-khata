package com.rishi.kiranaledger.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Stores enums as their names ("CASH", "ONLINE"). */
class Converters {
    @TypeConverter fun paymentModeToText(mode: PaymentMode): String = mode.name

    @TypeConverter fun textToPaymentMode(text: String): PaymentMode = PaymentMode.valueOf(text)

    @TypeConverter fun partyTypeToText(type: PartyType): String = type.name

    @TypeConverter fun textToPartyType(text: String): PartyType = PartyType.valueOf(text)

    @TypeConverter fun gstModeToText(mode: GstMode): String = mode.name

    @TypeConverter fun textToGstMode(text: String): GstMode = GstMode.valueOf(text)

    @TypeConverter fun stockReasonToText(reason: StockReason): String = reason.name

    @TypeConverter fun textToStockReason(text: String): StockReason = StockReason.valueOf(text)
}

// Every schema change bumps the version with a migration, so data on installed phones is kept.
// v2: customers.due_date
// v3: customers.party_type (suppliers), expenses table
// v4: stock fields on items, stock_moves table
// v5: cash_entries table (galla)
// v6: GST & HSN on items, bill_items and bills (hand-written MIGRATION_5_6)
// v7: buyer address/GSTIN on customers and bills (MIGRATION_6_7)
// v8: ship-to name/address/phone on bills (MIGRATION_7_8)
// v9: a saved shipping address on customers (MIGRATION_8_9)
@Database(
    entities = [Customer::class, Entry::class, Bill::class, BillItem::class, Item::class, Expense::class, StockMove::class, CashEntry::class],
    version = 9,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
@TypeConverters(Converters::class)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao

    abstract fun billDao(): BillDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun stockDao(): StockDao

    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile private var instance: LedgerDatabase? = null

        fun get(context: Context): LedgerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "ledger.db")
                    .addCallback(BalanceTriggers)
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build()
                    .also { instance = it }
            }

        /** A throwaway database in memory, with the same balance triggers — for tests only. */
        fun inMemoryForTests(context: Context): LedgerDatabase =
            Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
                .addCallback(BalanceTriggers)
                .build()

        /** The balance trigger SQL, so upgrade tests can recreate a v1 database exactly. */
        val triggerSql: List<String> get() = BalanceTriggers.statements

        /** Writes everything from the WAL journal into the main file, so copying ledger.db alone is a full backup. */
        fun checkpoint(context: Context) {
            get(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        }

        /** Closes the database before a restore replaces its files. */
        fun close() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}

/**
 * v5 → v6: adds GST & HSN columns. Only ADD COLUMN and CREATE INDEX, so no table is rebuilt —
 * existing rows and the balance triggers stay exactly as they are. Existing items start with no GST (0%),
 * and existing bills are marked UNREGISTERED so they keep printing as plain receipts.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "ALTER TABLE items ADD COLUMN hsn_code TEXT",
            "ALTER TABLE items ADD COLUMN gst_schedule TEXT",
            "ALTER TABLE items ADD COLUMN gst_entry_id TEXT",
            "ALTER TABLE items ADD COLUMN product_nature TEXT",
            "ALTER TABLE items ADD COLUMN cgst_milli INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE items ADD COLUMN price_includes_tax INTEGER NOT NULL DEFAULT 1",
            "CREATE INDEX IF NOT EXISTS index_items_hsn_code ON items (hsn_code)",
            "ALTER TABLE bill_items ADD COLUMN hsn_code TEXT",
            "ALTER TABLE bill_items ADD COLUMN cgst_milli INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE bill_items ADD COLUMN price_includes_tax INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE bill_items ADD COLUMN taxable_paise INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE bill_items ADD COLUMN cgst_paise INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE bill_items ADD COLUMN sgst_paise INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE bills ADD COLUMN gst_mode TEXT NOT NULL DEFAULT 'UNREGISTERED'",
        ).forEach(db::execSQL)
    }
}

/** v6 → v7: buyer address and GSTIN for the A4 invoice. ADD COLUMN only, so data and triggers are kept. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "ALTER TABLE customers ADD COLUMN address TEXT",
            "ALTER TABLE customers ADD COLUMN gstin TEXT",
            "ALTER TABLE bills ADD COLUMN party_address TEXT",
            "ALTER TABLE bills ADD COLUMN party_gstin TEXT",
        ).forEach(db::execSQL)
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customers ADD COLUMN ship_address TEXT")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "ALTER TABLE bills ADD COLUMN ship_name TEXT",
            "ALTER TABLE bills ADD COLUMN ship_address TEXT",
            "ALTER TABLE bills ADD COLUMN ship_phone TEXT",
        ).forEach(db::execSQL)
    }
}

/**
 * Keeps customers.balance_paise equal to the sum of that customer's non-deleted entries,
 * so app code can never forget to update a balance.
 */
private object BalanceTriggers : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        statements.forEach(db::execSQL)
    }

    val statements = listOf(
        """
            CREATE TRIGGER entry_insert AFTER INSERT ON entries
            BEGIN
                UPDATE customers
                SET balance_paise = balance_paise + (CASE WHEN NEW.deleted = 0 THEN NEW.amount_paise ELSE 0 END),
                    updated_at = NEW.updated_at
                WHERE id = NEW.customer_id;
            END
            """.trimIndent(),
        """
            CREATE TRIGGER entry_update AFTER UPDATE ON entries
            WHEN OLD.amount_paise <> NEW.amount_paise OR OLD.deleted <> NEW.deleted
            BEGIN
                UPDATE customers
                SET balance_paise = balance_paise
                    - (CASE WHEN OLD.deleted = 0 THEN OLD.amount_paise ELSE 0 END)
                    + (CASE WHEN NEW.deleted = 0 THEN NEW.amount_paise ELSE 0 END),
                    updated_at = NEW.updated_at
                WHERE id = NEW.customer_id;
            END
            """.trimIndent(),
    )
}
