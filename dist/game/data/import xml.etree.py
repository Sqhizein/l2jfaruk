import xml.etree.ElementTree as ET
import os

def scan_npc_skills(directory):
    seen_skills = set()
    
    with open('npc_skills.txt', 'w', encoding='utf-8') as f:
        for filename in os.listdir(directory):
            if filename.endswith('.xml'):
                file_path = os.path.join(directory, filename)
                try:
                    tree = ET.parse(file_path)
                    root = tree.getroot()
                    
                    for npc in root.findall('.//npc'):
                        npc_id = npc.get('id')
                        
                        for parameters in npc.findall('.//parameters'):
                            for skill in parameters.findall('./skill'):
                                skill_id = skill.get('id')
                                
                                if skill_id not in seen_skills:
                                    seen_skills.add(skill_id)
                                    
                                    skill_str = ET.tostring(skill, encoding='unicode').strip()
                                    
                                    next_node = skill.tail
                                    if next_node and '<!--' in next_node:
                                        comment = next_node[next_node.find('<!--'):next_node.find('-->')+3].strip()
                                        skill_str += ' ' + comment
                                    
                                    # NPC ID'sini en başa ekle
                                    f.write(f'NPC ID: {npc_id} | {skill_str}\n')
                            
                except ET.ParseError:
                    print(f"Hata: {filename} dosyası parse edilemedi")

# Kullanım
npc_directory = r"C:\Server\Server\game\data\stats\npcs"
scan_npc_skills(npc_directory)
print("npc_skills.txt dosyası oluşturuldu!")