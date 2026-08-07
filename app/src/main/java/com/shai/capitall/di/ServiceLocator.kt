package com.shai.capitall.di

import com.shai.capitall.data.repository.AuthRepository
import com.shai.capitall.data.repository.CategoryRepository
import com.shai.capitall.data.repository.PortfolioRepository
import com.shai.capitall.data.repository.SpaceRepository
import com.shai.capitall.data.repository.StockRepository
import com.shai.capitall.data.repository.TransactionRepository

/**
 * Service Locator פשוט (DI ידני): מספק מופע יחיד (singleton) לכל repository במקום
 * ליצור אחד חדש בכל מסך. כך נחסך יצירת אובייקטים חוזרת, מנוהל cache אחד ל-StockRepository,
 * וקל להחליף במופע מזויף בבדיקות (ה-setter פתוח ל-test בלבד).
 *
 * ה-ViewModels ממשיכים לקבל את ה-repository כפרמטר קונסטרקטור עם ברירת מחדל מכאן,
 * כדי לשמור על יכולת ההזרקה בבדיקות יחידה.
 */
object ServiceLocator {

    var portfolioRepository: PortfolioRepository = PortfolioRepository()
        internal set

    var transactionRepository: TransactionRepository = TransactionRepository()
        internal set

    var stockRepository: StockRepository = StockRepository()
        internal set

    var categoryRepository: CategoryRepository = CategoryRepository()
        internal set

    var authRepository: AuthRepository = AuthRepository()
        internal set

    var spaceRepository: SpaceRepository = SpaceRepository()
        internal set
}
