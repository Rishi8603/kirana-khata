package com.rishi.kiranaledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Summary shown on the Khata tab, for customers or suppliers. */
data class DueSummary(
    /** "Lena hai": sum of all negative balances (they owe the shop), as a positive number of paise. */
    val totalDuePaise: Long,
    val customersWithDue: Int,
    /** "Dena hai": sum of all positive balances (the shop owes them: advances, or supplier purchases on credit). */
    val totalAdvancePaise: Long,
    val partiesWeOwe: Int = 0,
)

/** Money still to collect, split by due date (overdue / today / upcoming). */
data class CollectionSummary(
    val overduePaise: Long,
    val overdueCount: Int,
    val todayPaise: Long,
    val todayCount: Int,
    val upcomingPaise: Long,
    val upcomingCount: Int,
)

/** Customer list filter, passed to [LedgerDao.customerList] as its ordinal. */
enum class CustomerFilter { ALL, DUE, SETTLED }

/** Customer list order, passed to [LedgerDao.customerList] as its ordinal. */
enum class CustomerSort { RECENT, HIGHEST_DUE, NAME, OLDEST }

/** All reads and writes for the ledger. Lists come back as Flows so screens update live. */
@Dao
interface LedgerDao {

    // ---- Customers ----

    /** Customers only (not suppliers) — used by the bill's customer picker. */
    @Query("SELECT * FROM customers WHERE deleted = 0 AND party_type = 'CUSTOMER' ORDER BY updated_at DESC")
    fun customers(): Flow<List<Customer>>

    @Query(
        "SELECT * FROM customers WHERE deleted = 0 AND party_type = 'CUSTOMER' " +
            "AND (name LIKE '%' || :text || '%' OR phone LIKE '%' || :text || '%') ORDER BY updated_at DESC",
    )
    fun searchCustomers(text: String): Flow<List<Customer>>

    /**
     * Search + filter + sort in one query for customers or suppliers.
     * [filter] and [sort] are CustomerFilter / CustomerSort ordinals. "Due" means money is owed:
     * a customer with a negative balance, or a supplier with a positive one.
     */
    @Query(
        "SELECT * FROM customers WHERE deleted = 0 AND party_type = :type " +
            "AND (:text = '' OR name LIKE '%' || :text || '%' OR phone LIKE '%' || :text || '%') " +
            "AND (:filter = 0 " +
            "OR (:filter = 1 AND ((:type = 'CUSTOMER' AND balance_paise < 0) OR (:type = 'SUPPLIER' AND balance_paise > 0))) " +
            "OR (:filter = 2 AND balance_paise = 0)) " +
            "ORDER BY CASE WHEN :sort = 1 THEN (CASE WHEN :type = 'SUPPLIER' THEN -balance_paise ELSE balance_paise END) END ASC, " +
            "CASE WHEN :sort = 2 THEN name END COLLATE NOCASE ASC, " +
            "CASE WHEN :sort = 3 THEN updated_at END ASC, " +
            "updated_at DESC",
    )
    fun customerList(type: PartyType, text: String, filter: Int, sort: Int): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun customer(id: String): Flow<Customer?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun customerOnce(id: String): Customer?

    @Query("UPDATE customers SET name = :name, phone = :phone, address = :address, gstin = :gstin, ship_address = :shipAddress WHERE id = :id")
    suspend fun updateCustomer(id: String, name: String, phone: String?, address: String?, gstin: String?, shipAddress: String?)

    @Query("UPDATE customers SET due_date = :day WHERE id = :id")
    suspend fun setDueDate(id: String, day: Long?)

    @Query(
        "SELECT COALESCE(-SUM(CASE WHEN due_date < :today THEN balance_paise END), 0) AS overduePaise, " +
            "COUNT(CASE WHEN due_date < :today THEN 1 END) AS overdueCount, " +
            "COALESCE(-SUM(CASE WHEN due_date = :today THEN balance_paise END), 0) AS todayPaise, " +
            "COUNT(CASE WHEN due_date = :today THEN 1 END) AS todayCount, " +
            "COALESCE(-SUM(CASE WHEN due_date > :today THEN balance_paise END), 0) AS upcomingPaise, " +
            "COUNT(CASE WHEN due_date > :today THEN 1 END) AS upcomingCount " +
            "FROM customers WHERE deleted = 0 AND party_type = 'CUSTOMER' AND balance_paise < 0 AND due_date IS NOT NULL",
    )
    fun collectionSummary(today: Long): Flow<CollectionSummary>

    /** Everyone who owes money: earliest due date first, those without a date last. */
    @Query(
        "SELECT * FROM customers WHERE deleted = 0 AND party_type = 'CUSTOMER' AND balance_paise < 0 " +
            "ORDER BY due_date IS NULL, due_date ASC, balance_paise ASC",
    )
    fun customersToCollect(): Flow<List<Customer>>

    @Query(
        "SELECT COALESCE(-SUM(CASE WHEN balance_paise < 0 THEN balance_paise END), 0) AS totalDuePaise, " +
            "COUNT(CASE WHEN balance_paise < 0 THEN 1 END) AS customersWithDue, " +
            "COALESCE(SUM(CASE WHEN balance_paise > 0 THEN balance_paise END), 0) AS totalAdvancePaise, " +
            "COUNT(CASE WHEN balance_paise > 0 THEN 1 END) AS partiesWeOwe " +
            "FROM customers WHERE deleted = 0 AND party_type = :type",
    )
    fun dueSummary(type: PartyType): Flow<DueSummary>

    @Insert
    suspend fun insertCustomer(customer: Customer)

    // ---- Entries ----

    @Query("SELECT * FROM entries WHERE customer_id = :customerId AND deleted = 0 ORDER BY entry_date DESC, created_at DESC")
    fun entries(customerId: String): Flow<List<Entry>>

    @Insert
    suspend fun insertEntry(entry: Entry)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: String): Entry?

    /** Edit an entry; the entry_update trigger moves the customer's balance by the difference. */
    @Query("UPDATE entries SET amount_paise = :amountPaise, note = :note, entry_date = :entryDate, updated_at = :now WHERE id = :id")
    suspend fun updateEntry(id: String, amountPaise: Long, note: String?, entryDate: Long, now: Long = System.currentTimeMillis())

    /** Soft delete: the row stays, so [undoDeleteEntry] can bring it back. Triggers fix the balance. */
    /** Soft delete: the customer leaves every list and total; their entries and bills stay in the records. */
    @Query("UPDATE customers SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun deleteCustomer(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE entries SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun deleteEntry(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE entries SET deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun undoDeleteEntry(id: String, now: Long = System.currentTimeMillis())

    /** A new customer from the bahi: the customer plus their opening-balance entry, saved together. */
    @Transaction
    suspend fun insertCustomerWithOpeningBalance(customer: Customer, openingDuePaise: Long) {
        insertCustomer(customer)
        if (openingDuePaise != 0L) {
            insertEntry(
                Entry(
                    customerId = customer.id,
                    // An old bahi balance is money owed: the customer owes us, or we owe the supplier.
                    amountPaise = customer.partyType.redSign() * openingDuePaise,
                    fromBahi = true,
                ),
            )
        }
    }
}
