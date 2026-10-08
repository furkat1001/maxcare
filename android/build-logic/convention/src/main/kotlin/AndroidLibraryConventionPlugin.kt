import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import uz.maxcare.buildlogic.AndroidConfig
import uz.maxcare.buildlogic.addCommonDependencies
import uz.maxcare.buildlogic.configureAndroidCommon
import uz.maxcare.buildlogic.configureKotlinQuality

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroidCommon()
            testOptions.targetSdk = AndroidConfig.TARGET_SDK
        }
        configureKotlinQuality()
        addCommonDependencies()
    }
}
