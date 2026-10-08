import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import uz.maxcare.buildlogic.AndroidConfig
import uz.maxcare.buildlogic.addCommonDependencies
import uz.maxcare.buildlogic.configureAndroidCommon
import uz.maxcare.buildlogic.configureKotlinQuality

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon()
            defaultConfig.targetSdk = AndroidConfig.TARGET_SDK
        }
        configureKotlinQuality()
        addCommonDependencies()
    }
}
