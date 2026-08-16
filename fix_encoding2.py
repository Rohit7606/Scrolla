import os
import glob

replacements = {
    'â”€': '─',
    'Â·': '·',
    'â€“': '–',
    'â€˜': '‘',
    'â€™': '’',
    'â€”': '—',
    'â€¢': '•',
    'â†’': '→'
}

files = glob.glob('app/src/main/kotlin/**/*.kt', recursive=True)
count = 0
for f in files:
    try:
        with open(f, 'r', encoding='utf-8') as file:
            content = file.read()
            
        modified = False
        for k, v in replacements.items():
            if k in content:
                content = content.replace(k, v)
                modified = True
                
        if modified:
            with open(f, 'w', encoding='utf-8', newline='') as file:
                file.write(content)
            count += 1
            print(f'Fixed {f}')
    except Exception as e:
        print(f'Error reading {f}: {e}')

print(f'Fixed {count} files.')
