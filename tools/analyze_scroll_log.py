#!/usr/bin/env python3
"""
Standalone script to analyze scroll log files containing deltaCm values.
Usage: python3 analyze_scroll_log.py <log_file_path>
"""

import sys
import re
import json

def parse_log_file(log_path):
    """Parse the log file and extract deltaCm data for analysis.
    Handles both JSON (Android Studio Logcat export) and plain text formats."""

    # Data structures for analysis
    all_delta_cm_values = []  # All deltaCm values (including resets) - for total calculation
    clean_line_data = []  # Tuples: (entry_index, full_message, deltaCm) - for finding max source line
    anomalies = []  # Format: (entry_index, full_message, deltaCm)

    try:
        with open(log_path, 'r', encoding='utf-8') as file:
            content = file.read()

        # Try to parse as JSON first (Android Studio Logcat export format)
        try:
            data = json.loads(content)
            entries = data.get("logcatMessages", [])

            # Track the delta from a RESET DETECTED line so we can skip its
            # paired duplicate (the app logs the same delta twice: once with
            # the "RESET DETECTED" prefix and once without).
            last_reset_delta = None

            for entry_index, entry in enumerate(entries, start=1):
                message = entry.get("message", "")

                # Check if message contains deltaCm
                if 'deltaCm=' in message:
                    match = re.search(r'deltaCm=(-?\d+\.?\d*|-?\.\d+)', message)

                    if match:
                        delta_cm = float(match.group(1))

                        # Skip the immediate next entry if it is the unprefixed
                        # duplicate of the just-seen RESET DETECTED line.
                        if last_reset_delta is not None and delta_cm == last_reset_delta and 'RESET DETECTED' not in message:
                            last_reset_delta = None  # consume the skip (applies only to the immediate next)
                            continue

                        # Add to all deltas for total including resets
                        all_delta_cm_values.append(delta_cm)

                        # Check if it's a RESET DETECTED message
                        if 'RESET DETECTED' in message:
                            # Record this delta so we can skip its paired duplicate on the next iteration
                            last_reset_delta = delta_cm
                        else:
                            # Non-reset message - track for max finding
                            clean_line_data.append((entry_index, message, delta_cm))

                            # Check for anomaly (> 20cm)
                            if delta_cm > 20.0:
                                anomalies.append((entry_index, message, delta_cm))

            return {
                'total_lines': len(entries),
                'reset_lines_count': len(entries) - len(clean_line_data),
                'all_delta_cm_values': all_delta_cm_values,
                'clean_line_data': clean_line_data,
                'anomalies': anomalies
            }

        except json.JSONDecodeError:
            # Fall back to plain-text line-by-line parsing
            lines = content.strip().split('\n')

            # Track the delta from a RESET DETECTED line so we can skip its
            # paired duplicate (the app logs the same delta twice: once with
            # the "RESET DETECTED" prefix and once without).
            last_reset_delta = None

            for line_number, line in enumerate(lines, start=1):
                line = line.strip()

                # Check if line contains deltaCm
                if 'deltaCm=' in line:
                    match = re.search(r'deltaCm=(-?\d+\.?\d*|-?\.\d+)', line)

                    if match:
                        delta_cm = float(match.group(1))

                        # Skip the immediate next entry if it is the unprefixed
                        # duplicate of the just-seen RESET DETECTED line.
                        if last_reset_delta is not None and delta_cm == last_reset_delta and 'RESET DETECTED' not in line:
                            last_reset_delta = None  # consume the skip (applies only to the immediate next)
                            continue

                        # Add to all deltas for total including resets
                        all_delta_cm_values.append(delta_cm)

                        # Check if it's a RESET DETECTED line
                        if 'RESET DETECTED' in line:
                            # Record this delta so we can skip its paired duplicate on the next iteration
                            last_reset_delta = delta_cm
                        else:
                            # Non-reset line - track for max finding
                            clean_line_data.append((line_number, line, delta_cm))

                            # Check for anomaly (> 20cm)
                            if delta_cm > 20.0:
                                anomalies.append((line_number, line, delta_cm))

            return {
                'total_lines': len(lines),
                'reset_lines_count': len(lines) - len(clean_line_data),
                'all_delta_cm_values': all_delta_cm_values,
                'clean_line_data': clean_line_data,
                'anomalies': anomalies
            }

    except FileNotFoundError:
        print(f"Error: Log file not found at '{log_path}'")
        return None
    except Exception as e:
        print(f"Error reading log file: {e}")
        return None

def print_analysis_results(results):
    """Print the analysis results in a readable format."""

    print("\n=== SCROLL LOG ANALYSIS RESULTS ===\n")

    print(f"Total lines processed: {results['total_lines']}")
    print(f"RESET DETECTED lines: {results['reset_lines_count']}")
    print(f"Non-reset lines: {results['total_lines'] - results['reset_lines_count']}")

    # Calculate totals
    total_including_resets = sum(results['all_delta_cm_values'])
    total_excluding_resets = sum(item[2] for item in results['clean_line_data'])

    print(f"\n--- DISTANCE TOTALS ---")
    print(f"Total INCLUDING RESET DETECTED lines: {total_including_resets:.3f} cm")
    print(f"Total EXCLUDING RESET DETECTED lines: {total_excluding_resets:.3f} cm")

    # Find largest delta (excluding reset lines)
    if results['clean_line_data']:
        max_entry = max(results['clean_line_data'], key=lambda x: x[2])
        max_line_number, max_line, max_delta_cm = max_entry
        print(f"\n--- LARGEST INDIVIDUAL DISTANCE (excluding resets) ---")
        print(f"Value: {max_delta_cm:.3f} cm")
        print(f"Line {max_line_number}: {max_line}")

    # Anomalies report
    if results['anomalies']:
        print(f"\n--- POSSIBLE ANOMALIES (> 20cm per event) ---")
        for line_num, line, delta_cm in results['anomalies']:
            print(f"Line {line_num}: {delta_cm:.3f} cm - {line}")
    else:
        print(f"\n--- ANOMALIES ---")
        print("No anomalies detected (no events > 20cm)")

def main():
    """Main function to handle command-line execution."""

    # Check command-line arguments
    if len(sys.argv) != 2:
        print("Usage: python3 analyze_scroll_log.py <log_file_path>")
        print("Example: python3 analyze_scroll_log.py s0.7_instagram_test.txt")
        sys.exit(1)

    log_path = sys.argv[1]

    print(f"Analyzing log file: {log_path}")

    # Parse and analyze the log file
    results = parse_log_file(log_path)

    if results is not None:
        print_analysis_results(results)
    else:
        print("Analysis failed - exiting")
        sys.exit(1)

if __name__ == "__main__":
    main()