# Homer Stats

A lightweight statistics collector for a smart home. 

Periodically collects home climate data (temperature, humidity, air quality) from smart sensors and sends it to VictoriaMetrics, enabling visualization in Grafana.

Built with Kotlin and Ktor, designed to run locally on a Raspberry Pi. Docker containers with Homebridge, VictoriaMetrics, Prometheus node_exporter, and Grafana are part of the setup - all running on the same Raspberry Pi device.
