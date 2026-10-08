package uz.maxcare.kids.protection

import kotlin.time.Duration
import uz.maxcare.kids.core.protection.ChangePasswordResult
import uz.maxcare.kids.core.protection.ParentPassword
import uz.maxcare.kids.core.protection.PasswordCheck
import uz.maxcare.kids.core.protection.SetPasswordResult

/** Возвращает заданный результат проверки; как и настоящий, затирает переданный массив. */
internal class FakeParentPassword(var result: PasswordCheck = PasswordCheck.Correct) :
    ParentPassword {
    var verifyCalls = 0
        private set

    override suspend fun isSet() = true

    override suspend fun setInitial(password: CharArray): SetPasswordResult {
        password.fill('\u0000')
        return SetPasswordResult.AlreadySet
    }

    override suspend fun verify(password: CharArray): PasswordCheck {
        verifyCalls++
        password.fill('\u0000')
        return result
    }

    override suspend fun change(current: CharArray, new: CharArray): ChangePasswordResult {
        current.fill('\u0000')
        new.fill('\u0000')
        return ChangePasswordResult.Rejected(result)
    }

    override suspend fun lockoutRemaining() = Duration.ZERO
}
