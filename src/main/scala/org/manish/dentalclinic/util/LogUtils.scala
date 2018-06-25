package org.manish.dentalclinic.util

import org.slf4j.LoggerFactory

trait LogUtils {
  def logger = LoggerFactory.getLogger(this.getClass.getSimpleName)
}
